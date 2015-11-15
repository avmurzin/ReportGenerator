package com.avmurzin.report;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TimeZone;

import javax.annotation.Resource;

import org.hibernate.Hibernate;
import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.hibernate.classic.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import com.avmurzin.report.domain.Step;
import com.avmurzin.report.domain.Task;

/**
 * Набор методов для вычисления отчетов.
 * @author murzin
 *
 */
@ContextConfiguration
@Transactional
public class Calculator {
	
	DateFormat inFormat = new SimpleDateFormat( "yyyy-MM-dd hh:mm:ss");
	Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
	
	 final static Charset ENCODING = StandardCharsets.UTF_8;
	
	/**
	 * Экспрорт данных их текстового файла в MySQL.
	 * @return
	 * @throws ParseException 
	 */
	public boolean fillDatabase(String filename) throws IOException, ParseException {

		Path fFilePath = Paths.get(filename);
		try (Scanner scanner =  new Scanner(fFilePath, ENCODING.name())){
		      while (scanner.hasNextLine()){
		        processLine(scanner.nextLine());
		      }      
		    }
		return true;
	}
	
	/**
	 * Отчет по числу задач в статусе "Выполнена" с разбивкой по типу и с числом просроченных.
	 * @param timeUp
	 * @param timeDown
	 * @return
	 */
	public boolean calcTaskByType(Long timeUp, Long timeDown) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		session.beginTransaction();
		
		Map<String, int[]> report = new HashMap<String, int[]>();
		int[] data;
		Query query;
		
		//создать список типов задач (по всему массиву данных), должен вернуться массив строк.
		query = session.createSQLQuery("SELECT DISTINCT type FROM task");
		List<String> result = (List<String>) query.list();
		
		for (String s : result) {
			data = new int[] {0, 0};
			report.put(s, data);
			//System.out.println(s + ":" + report.get(s)[0]+ ":" + report.get(s)[0]);
		}

		//список задач, момент выполнения которых попадает в заданный интервал с указанием времени выполненения
		Map<String, Long> tasks = new HashMap<String, Long>();
		query = session.createSQLQuery("SELECT taskId as id, MAX(stepUpTime) as max from step s WHERE s.step='Выполнена' AND s.stepUpTime >= :stepUpTime AND s.stepUpTime <= :stepDownTime GROUP BY taskId")
				.addScalar("id", org.hibernate.type.StandardBasicTypes.STRING)
				.addScalar("max", org.hibernate.type.StandardBasicTypes.LONG);

		query.setParameter("stepUpTime", Long.toString(timeUp));
		query.setParameter("stepDownTime", Long.toString(timeDown));
		
		List list = query.list();
		System.out.println("Найдено задач: " + list.size());
		
		Object[] array;
		
		for (int i=0; i < list.size(); i++) {
			array = (Object[]) list.get(i);
			tasks.put((String) array[0], (Long) array[1]);
		}
		
		
		//для каждой задачи считаем типы и выход за крайний срок
		for(String taskId : tasks.keySet()) {
			query = session.createSQLQuery("SELECT * FROM task t WHERE t.taskId = :taskId AND manager IN (SELECT user FROM otkis)").addEntity(Task.class).setParameter("taskId", taskId);
			
			if(query.list().size() != 0) {
				Task task = (Task) query.list().get(0);

				//увеличиваем число задач соотв. типа
				report.get(task.getType())[0]++;

				//если просрочено, то увеличиваем число просрочек для этого типа
				if (task.getLastTime() < tasks.get(taskId)) {
					report.get(task.getType())[1]++;
					System.out.println("Просрочено:" + task.getTaskId() + ":" + task.getSubject() + ":" + task.getLastTime());
				}
			}
		}
		
		//вывод отчета
		for(String type : report.keySet()) {
			System.out.println(type + ":" +report.get(type)[0] + ":" + report.get(type)[1]);
		}
		
		session.getTransaction().commit();
		session.close();
		return true;
	}

	/**
	 * Отчет по числу задач в статусе "Выполнена" с разбивкой по типу, классу сервиса и с числом просроченных.
	 * @param timeUp
	 * @param timeDown
	 * @return
	 */
	public boolean calcTaskByClassification(Long timeUp, Long timeDown) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		session.beginTransaction();
		
		int[] data;
		Query query;
		List<String> result, classResult;
		
		//карта классов задач со счетчиками всего:просрочено
		Map<String, int[]> subReport;
		//отчета - карта типов задач, содержащая карту классов со счетчиками
		Map<String, Map<String, int[]>> report = new HashMap<String, Map<String, int[]>>();
		
		//создать список типов задач (по всему массиву данных), должен вернуться массив строк.
		query = session.createSQLQuery("SELECT DISTINCT type FROM task");
		result = (List<String>) query.list();
		
		for (String s : result) {
			//создать список классов задач для кадого типа (по всему массиву данных), должен вернуться массив строк.
			query = session.createSQLQuery("SELECT DISTINCT classification FROM task t WHERE t.type = :type")
					.setParameter("type", s);
			classResult = (List<String>) query.list();
			subReport = new HashMap<String, int[]>();
			for(String cls : classResult) {
				data = new int[] {0, 0};
				subReport.put(cls, data);
			}
			report.put(s, subReport);
		}
		
		//список задач, момент выполнения которых попадает в заданный интервал с указанием времени выполненения
		Map<String, Long> tasks = new HashMap<String, Long>();
		query = session.createSQLQuery("SELECT taskId as id, MAX(stepUpTime) as max from step s WHERE s.step='Выполнена' AND s.stepUpTime >= :stepUpTime AND s.stepUpTime <= :stepDownTime GROUP BY taskId")
				.addScalar("id", org.hibernate.type.StandardBasicTypes.STRING)
				.addScalar("max", org.hibernate.type.StandardBasicTypes.LONG);

		query.setParameter("stepUpTime", Long.toString(timeUp));
		query.setParameter("stepDownTime", Long.toString(timeDown));
		
		List list = query.list();
		System.out.println("Найдено задач: " + list.size());
		
		Object[] array;
		
		for (int i=0; i < list.size(); i++) {
			array = (Object[]) list.get(i);
			tasks.put((String) array[0], (Long) array[1]);
		}
		
		//для каждой задачи считаем типы и выход за крайний срок
		for(String taskId : tasks.keySet()) {
			query = session.createSQLQuery("SELECT * FROM task t WHERE t.taskId = :taskId AND manager IN (SELECT user FROM otkis)").addEntity(Task.class).setParameter("taskId", taskId);
			
			if(query.list().size() != 0) {
				Task task = (Task) query.list().get(0);

				//увеличиваем число задач соотв. типа
				report.get(task.getType()).get(task.getClassification())[0]++;

				//если просрочено, то увеличиваем число просрочек для этого типа
				if (task.getLastTime() < tasks.get(taskId)) {
					report.get(task.getType()).get(task.getClassification())[1]++;
					//System.out.println("Просрочено:" + task.getTaskId() + ":" + task.getSubject() + ":" + task.getLastTime());
				}
			}
		}
		
		//вывод отчета
		for(String type : report.keySet()) {
			for (String cls : report.get(type).keySet()) {
				System.out.println(type + ":" + cls + ":" + report.get(type).get(cls)[0] + ":" + report.get(type).get(cls)[1]);
			}
			
		}
		
		session.getTransaction().commit();
		session.close();
		return true;
	}
	
	/**
	 * Отчет по числу задач в статусе "Выполнена" с разбивкой по подотчетному лицу и с числом просроченных.
	 * @param timeUp
	 * @param timeDown
	 * @return
	 */
	public boolean calcTaskByManager(Long timeUp, Long timeDown) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		session.beginTransaction();
		
		Map<String, int[]> report = new HashMap<String, int[]>();
		int[] data;
		Query query;
		
		//создать список исполнителей задач (по всему массиву данных), должен вернуться массив строк.
		query = session.createSQLQuery("SELECT DISTINCT user FROM otkis");
		List<String> result = (List<String>) query.list();
		
		for (String s : result) {
			data = new int[] {0, 0};
			report.put(s, data);
			//System.out.println(s + ":" + report.get(s)[0]+ ":" + report.get(s)[0]);
		}

		//список задач, момент выполнения которых попадает в заданный интервал с указанием времени выполненения
		Map<String, Long> tasks = new HashMap<String, Long>();
		query = session.createSQLQuery("SELECT taskId as id, MAX(stepUpTime) as max from step s WHERE s.step='Выполнена' AND s.stepUpTime >= :stepUpTime AND s.stepUpTime <= :stepDownTime GROUP BY taskId")
				.addScalar("id", org.hibernate.type.StandardBasicTypes.STRING)
				.addScalar("max", org.hibernate.type.StandardBasicTypes.LONG);

		query.setParameter("stepUpTime", Long.toString(timeUp));
		query.setParameter("stepDownTime", Long.toString(timeDown));
		
		List list = query.list();
		System.out.println("Найдено задач: " + list.size());
		
		Object[] array;
		
		for (int i=0; i < list.size(); i++) {
			array = (Object[]) list.get(i);
			tasks.put((String) array[0], (Long) array[1]);
		}
		
		
		//для каждой задачи считаем типы и выход за крайний срок
		for(String taskId : tasks.keySet()) {
			query = session.createSQLQuery("SELECT * FROM task t WHERE t.taskId = :taskId AND manager IN (SELECT user FROM otkis)").addEntity(Task.class).setParameter("taskId", taskId);
			
			if(query.list().size() != 0) {
				Task task = (Task) query.list().get(0);

				//увеличиваем число задач соотв. типа
				report.get(task.getManager())[0]++;

				//если просрочено, то увеличиваем число просрочек для этого типа
				if (task.getLastTime() < tasks.get(taskId)) {
					report.get(task.getManager())[1]++;
					System.out.println("Просрочено:" + task.getTaskId() + ":" + task.getSubject() + ":" + task.getLastTime());
				}
			}
		}
		
		//вывод отчета
		for(String type : report.keySet()) {
			System.out.println(type + ":" +report.get(type)[0] + ":" + report.get(type)[1]);
		}
		
		session.getTransaction().commit();
		session.close();
		return true;
	}
	
	/**
	 * Обработка каждой строки входного файла при заполнении базы данных.
	 * @param aLine
	 * @throws ParseException
	 */
	private void processLine (String aLine) throws ParseException {
		Session session = HibernateUtil.getSessionFactory().openSession();
		session.beginTransaction();
	    Task task;
	    Step step;

		
		Scanner scanner = new Scanner(aLine);
	    scanner.useDelimiter(";");
	    
    	String taskId = "NA";
		String subject = "NA";
		String type = "NA";
		String classification = "NA";
		String originator = "NA";
		Long lastTime = 1577836800L; 
		String manager = "NA";
		String stepName = "NA";
		Long stepUpTime = 0L;
		Long stepDownTime = 0L;
		String worker = "NA";	    
	    try {
		    if (scanner.hasNext()){ taskId = scanner.next(); }
		    if (scanner.hasNext()){	subject = scanner.next(); }
		    if (scanner.hasNext()){	type = scanner.next(); }
		    if (scanner.hasNext()){	classification = scanner.next(); }
		    if (scanner.hasNext()){	originator = scanner.next(); }
		    if (scanner.hasNext()){	calendar.setTime(inFormat.parse(scanner.next())); 
				lastTime = calendar.getTimeInMillis()/1000; 
				if(lastTime < 0) {
					lastTime = 1577836800L;
				}
		    }
		    if (scanner.hasNext()){	manager = scanner.next(); }
		    if (scanner.hasNext()){	stepName = scanner.next(); }
		    if (scanner.hasNext()){	calendar.setTime(inFormat.parse(scanner.next()));
				stepUpTime = calendar.getTimeInMillis()/1000;
		    }
		    if (scanner.hasNext()){	calendar.setTime(inFormat.parse(scanner.next()));
				stepDownTime = calendar.getTimeInMillis()/1000;
		    }
		    if (scanner.hasNext()){	worker = scanner.next(); }
	    } 
	    catch (Exception e) {}
	    finally {
	    	
			//проверить нет ли уже такой задачи
			Query query = session.createSQLQuery("SELECT * FROM task t WHERE t.taskId = :taskId").addEntity(Task.class).setParameter("taskId", taskId);
			List result = query.list();
			
			if (result.isEmpty()) {
				task = new Task();
				task.setClassification(classification);
				task.setLastTime(lastTime);
				task.setManager(manager);
				task.setOriginator(originator);
				task.setSubject(subject);
				task.setTaskId(taskId);
				task.setType(type);
				
				step = new Step();
				step.setStep(stepName);
				step.setStepDownTime(stepDownTime);
				step.setStepUpTime(stepUpTime);
				step.setWorker(worker);
				step.setTaskId(taskId);
				
				session.save(task);
				session.save(step);
				session.flush();
				session.clear();
			} else {
				step = new Step();
				step.setStep(stepName);
				step.setStepDownTime(stepDownTime);
				step.setStepUpTime(stepUpTime);
				step.setWorker(worker);
				task = (Task) result.get(0);
				step.setTaskId(task.getTaskId());
				
				session.save(step);
				session.flush();
				session.clear();
			}
	    	
			session.getTransaction().commit();
	    	session.close();
	    }
	    
	    scanner.close();
	}
}

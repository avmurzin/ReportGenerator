package com.avmurzin.report;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
	
	DateFormat inFormat = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss");
	//DateFormat inFormat = new SimpleDateFormat( "d-MM-yyyy h:mm:ss a");
	Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
	
	class ReportData {
		int taskCount = 0;
		int noSlaCount = 0;
		List<Long> duration = new ArrayList<Long>();
	}
	
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
	public boolean calcTaskByType(Long timeUp, Long timeDown, String delimeter) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		session.beginTransaction();
		
		Map<String, ReportData> report = new HashMap<String, ReportData>();
		ReportData data;
		Query query;
		
		//создать список типов задач (по всему массиву данных), должен вернуться массив строк.
		query = session.createSQLQuery("SELECT DISTINCT type FROM task");
		List<String> result = (List<String>) query.list();
		
		for (String s : result) {
			//data = new int[] {0, 0};
			data = new ReportData();
			report.put(s, data);
			//System.out.println(s + ":" + report.get(s)[0]+ ":" + report.get(s)[0]);
		}

		//список задач, момент выполнения которых попадает в заданный интервал с указанием времени выполненения
		Map<String, Long> tasks = new HashMap<String, Long>();
		query = session.createSQLQuery("SELECT s.taskId as id, MAX(s.stepUpTime) as max from step s WHERE s.step='Выполнена' AND s.stepUpTime >= :stepUpTime AND s.stepUpTime <= :stepDownTime GROUP BY s.taskId")
				.addScalar("id", org.hibernate.type.StandardBasicTypes.STRING)
				.addScalar("max", org.hibernate.type.StandardBasicTypes.LONG);

		query.setParameter("stepUpTime", Long.toString(timeUp));
		query.setParameter("stepDownTime", Long.toString(timeDown));
		
		List list = query.list();
		//System.out.println("Найдено задач: " + list.size());
		
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
				
				query = session.createSQLQuery("SELECT MAX(stepUpTime) as max from step s WHERE s.step='Регистрация' AND taskId = :taskId")
						.addScalar("max", org.hibernate.type.StandardBasicTypes.LONG);
				query.setParameter("taskId", taskId);
				
				Long registration = (Long) query.list().get(0);

				//увеличиваем число задач соотв. типа
				report.get(task.getType()).taskCount++;
				
				//сохраняем продоложительность
				//System.out.println(taskId + ":" + registration + ":" + tasks.get(taskId) + ":" + getDuration(registration, tasks.get(taskId)) / 3600d);
				report.get(task.getType()).duration.add(getDuration(registration, tasks.get(taskId)));

				//если просрочено, то увеличиваем число просрочек для этого типа
				if (task.getLastTime() < tasks.get(taskId)) {
					report.get(task.getType()).noSlaCount++;
					//System.out.println("Просрочено:" + task.getTaskId() + ":" + task.getSubject() + ":" + task.getLastTime());
				}
			}
		}
		
		//вывод отчета
		System.out.println("Тип_задачи" 
				+ delimeter 
				+ "Выполнено_задач" 
				+ delimeter 
				+ "Просрочено_задач" 
				+ delimeter
				+ "Среднее_время"
				+ delimeter
				+ "Медианное_время");
		for(String type : report.keySet()) {
			System.out.println(type 
					+ delimeter 
					+report.get(type).taskCount 
					+ delimeter 
					+ report.get(type).noSlaCount 
					+ delimeter
					+ getAverage(report.get(type).duration)/3600d 
					+ delimeter
					+ getMedian(report.get(type).duration)/3600d);
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
	public boolean calcTaskByClassification(Long timeUp, Long timeDown, String delimeter) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		session.beginTransaction();
		
		ReportData data;
		Query query;
		List<String> result, classResult;
		
		//карта классов задач со счетчиками всего:просрочено
		Map<String, ReportData> subReport;
		//отчета - карта типов задач, содержащая карту классов со счетчиками
		Map<String, Map<String, ReportData>> report = new HashMap<String, Map<String, ReportData>>();
		
		//создать список типов задач (по всему массиву данных), должен вернуться массив строк.
		query = session.createSQLQuery("SELECT DISTINCT type FROM task");
		result = (List<String>) query.list();
		
		for (String s : result) {
			//создать список классов задач для кадого типа (по всему массиву данных), должен вернуться массив строк.
			query = session.createSQLQuery("SELECT DISTINCT classification FROM task t WHERE t.type = :type")
					.setParameter("type", s);
			classResult = (List<String>) query.list();
			subReport = new HashMap<String, ReportData>();
			for(String cls : classResult) {
				data = new ReportData();
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
		//System.out.println("Найдено задач: " + list.size());
		
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
				
				query = session.createSQLQuery("SELECT MAX(stepUpTime) as max from step s WHERE s.step='Регистрация' AND taskId = :taskId")
						.addScalar("max", org.hibernate.type.StandardBasicTypes.LONG);
				query.setParameter("taskId", taskId);
				
				Long registration = (Long) query.list().get(0);

				//увеличиваем число задач соотв. типа
				report.get(task.getType()).get(task.getClassification()).taskCount++;
				
				//сохраняем продоложительность
				report.get(task.getType()).get(task.getClassification()).duration.add(getDuration(registration, tasks.get(taskId)));

				//если просрочено, то увеличиваем число просрочек для этого типа
				if (task.getLastTime() < tasks.get(taskId)) {
					report.get(task.getType()).get(task.getClassification()).noSlaCount++;
					//System.out.println("Просрочено:" + task.getTaskId() + ":" + task.getSubject() + ":" + task.getLastTime());
				}
			}
		}
		
		//вывод отчета
		System.out.println("Тип_задачи" 
				+ delimeter
				+ "Классификация_задачи"
				+ delimeter
				+ "Выполнено_задач" 
				+ delimeter
				+ "Просрочено_задач" 
				+ delimeter
				+ "Среднее_время" 
				+ delimeter
				+ "Медианное_время");
		for(String type : report.keySet()) {
			for (String cls : report.get(type).keySet()) {
				System.out.println(type 
						+ delimeter
						+ cls 
						+ delimeter
						+ report.get(type).get(cls).taskCount 
						+ delimeter
						+ report.get(type).get(cls).noSlaCount 
						+ delimeter
						+ getAverage(report.get(type).get(cls).duration)/3600d 
						+ delimeter
						+ getMedian(report.get(type).get(cls).duration)/3600d);
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
	public boolean calcTaskByManager(Long timeUp, Long timeDown, String delimeter) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		session.beginTransaction();
		
		Map<String, ReportData> report = new HashMap<String, ReportData>();
		ReportData data;
		Query query;
		
		//создать список исполнителей задач (по всему массиву данных), должен вернуться массив строк.
		query = session.createSQLQuery("SELECT DISTINCT user FROM otkis");
		List<String> result = (List<String>) query.list();
		
		for (String s : result) {
			data = new ReportData();
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
		//System.out.println("Найдено задач: " + list.size());
		
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
				
				query = session.createSQLQuery("SELECT MAX(stepUpTime) as max from step s WHERE s.step='Регистрация' AND taskId = :taskId")
						.addScalar("max", org.hibernate.type.StandardBasicTypes.LONG);
				query.setParameter("taskId", taskId);
				
				Long registration = (Long) query.list().get(0);

				//увеличиваем число задач соотв. типа
				report.get(task.getManager()).taskCount++;
				
				//сохраняем продоложительность
				//System.out.println(taskId + ":" + registration + ":" + tasks.get(taskId) + ":" + getDuration(registration, tasks.get(taskId)) / 3600d);
				report.get(task.getManager()).duration.add(getDuration(registration, tasks.get(taskId)));

				//если просрочено, то увеличиваем число просрочек для этого типа
				if (task.getLastTime() < tasks.get(taskId)) {
					report.get(task.getManager()).noSlaCount++;
					//System.out.println("Просрочено:" + task.getTaskId() + ":" + task.getSubject() + ":" + task.getLastTime());
				}
			}
		}
		
		//вывод отчета
		System.out.println("Исполнитель" 
				+ delimeter
				+ "Выполнено_задач"
				+ delimeter
				+ "Просрочено_задач"
				+ delimeter
				+ "Среднее_время"
				+ delimeter
				+ "Медианное время");
		for(String type : report.keySet()) {
			System.out.println(type 
					+ delimeter
					+report.get(type).taskCount 
					+ delimeter
					+ report.get(type).noSlaCount
					+ delimeter
					+ getAverage(report.get(type).duration)/3600d
					+ delimeter
					+ getMedian(report.get(type).duration)/3600d);
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
	
	/**
	 * Вычисление продолжительности задачи (между регистрацией и выполнением).
	 * Вычитается по 12 часов в каждых сутках, если начало и конец расположены в разных днях.
	 * @param registration
	 * @param commit
	 * @return
	 */
	private Long getDuration(Long registration, Long commit) {
		if (registration > commit) {
			return 0L;
		}
		
		Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		calendar.setTimeInMillis(registration*1000);
		int dayReg = calendar.get(Calendar.DAY_OF_MONTH);
		calendar.setTimeInMillis(commit*1000);
		int dayComm = calendar.get(Calendar.DAY_OF_MONTH);
		if(dayReg < dayComm) {
			return commit - registration - (dayComm-dayReg)*43200;
		} 
		if(dayReg > dayComm) {
			return commit - registration - (dayComm-dayReg+30)*43200;
		} else {
			return commit - registration;
		}
		//return commit - registration;
	}
	
	/**
	 * Вычисление медианного значения ряда.
	 * @param list
	 * @return
	 */
	private double getAverage(List<Long> list) {
		if (list.size() == 0) {
			return 0L;
		}
		Long summ = 0L;
		for (int i = 0; i < list.size(); i++) {
			summ = summ + list.get(i);
		}
		return summ / list.size();
	}

	private double getMedian(List<Long> list) {
		if (list.size() == 0) {
			return 0L;
		}
		Long[] array = new Long[list.size()];
		for (int i = 0; i < list.size(); i++) {
			array[i] = list.get(i);
		}
		Arrays.sort(array);
		double median;
		if (array.length % 2 == 0)
		    median = ((double)array[array.length/2] + (double)array[array.length/2 - 1])/2;
		else
		    median = (double) array[array.length/2];
		
		return median;
	}
	
}

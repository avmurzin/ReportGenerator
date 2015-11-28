package com.avmurzin.report;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

//import org.hibernate.SessionFactory;
//import org.hibernate.classic.Session;
//import org.springframework.beans.factory.annotation.Autowired;

public class Starter {
	
//	@Autowired
//	private SessionFactory sessionFactory;
	
	public static void main(String[] args) throws ParseException {
		
		DateFormat inFormat = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss");
		Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		Long timeUp, timeDown;
		
		Calculator calc = new Calculator();
		
		
		if (args != null && args.length > 0 && args[0].equals("fill")) {
			System.out.println("Fill database...");
			try {
				calc.fillDatabase("/home/murzin/Соя/отчет_28.11/Заявки_в_ИТИЛ_28.11.csv");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if(args != null && args.length > 2) {
			calendar.setTime(inFormat.parse(args[1]));
			timeUp = calendar.getTimeInMillis()/1000;
			calendar.setTime(inFormat.parse(args[2]));
			timeDown = calendar.getTimeInMillis()/1000;
			
			//System.out.println(timeUp + ":" + timeDown);
			System.out.println("Time_period" + ":" + args[1] + ":" + args[2]);
			System.out.println("");
			System.out.println("Выполнено задач группой администраторов");
			calc.calcTaskByType(timeUp, timeDown, ";", "otkis_admin");
			System.out.println("");
			System.out.println("Выполнено задач группой поддержки");
			calc.calcTaskByType(timeUp, timeDown, ";", "otkis_support");
			System.out.println("");
			System.out.println("Выполнено задач разной классификации группой администраторов");
			calc.calcTaskByClassification(timeUp, timeDown, ";", "otkis_admin");
			System.out.println("");
			System.out.println("Выполнено задач разной классификации группой поддержки");
			calc.calcTaskByClassification(timeUp, timeDown, ";", "otkis_support");
			System.out.println("");
			System.out.println("Выполнено задач группой администраторов по персоналиям");
			calc.calcTaskByManager(timeUp, timeDown, ";", "otkis_admin");
			System.out.println("");
			System.out.println("Выполнено задач группой поддержки по персоналиям");
			calc.calcTaskByManager(timeUp, timeDown, ";", "otkis_support");
		}
		

	}
}

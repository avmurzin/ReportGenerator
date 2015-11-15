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
		
		DateFormat inFormat = new SimpleDateFormat( "yyyy-MM-dd hh:mm:ss");
		Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		Long timeUp, timeDown;
		
		Calculator calc = new Calculator();
		
		if (args != null && args.length > 0 && args[0].equals("fill")) {
			System.out.println("Fill database...");
			try {
				calc.fillDatabase("/home/murzin/mnt/sdb1/storage/java_programming/отчет_по_всем_задачам_11.11.2015.csv");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if(args != null && args.length > 2) {
			calendar.setTime(inFormat.parse(args[1]));
			timeUp = calendar.getTimeInMillis()/1000;
			calendar.setTime(inFormat.parse(args[2]));
			timeDown = calendar.getTimeInMillis()/1000;
			
			System.out.println(timeUp + ":" + timeDown);
			
			calc.calcTaskByType(timeUp, timeDown);
			calc.calcTaskByClassification(timeUp, timeDown);
			calc.calcTaskByManager(timeUp, timeDown);
		}
		

	}
}

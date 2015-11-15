package com.avmurzin.report.domain;

import java.util.Collection;
import java.util.LinkedHashSet;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table(name="task")
public class Task {
		
		@Id
		@GeneratedValue(strategy=GenerationType.AUTO)
		private Long id;
		
		private String taskId;
		private String subject;
		private String type;
		private String classification;
		private String originator;
		private Long lastTime;
		private String manager;
		
//		@OneToMany(cascade=CascadeType.ALL)
//		@JoinColumn(name="task_id")
//		private Collection<Step> steps = new LinkedHashSet<Step>();
		
		public String getTaskId() {
			return taskId;
		}
		public void setTaskId(String taskId) {
			this.taskId = taskId;
		}
		public String getSubject() {
			return subject;
		}
		public void setSubject(String subject) {
			this.subject = subject;
		}
		public String getType() {
			return type;
		}
		public void setType(String type) {
			this.type = type;
		}
		public String getClassification() {
			return classification;
		}
		public void setClassification(String classification) {
			this.classification = classification;
		}
		public String getOriginator() {
			return originator;
		}
		public void setOriginator(String originator) {
			this.originator = originator;
		}
		public Long getLastTime() {
			return lastTime;
		}
		public void setLastTime(Long lastTime) {
			this.lastTime = lastTime;
		}
		public String getManager() {
			return manager;
		}
		public void setManager(String manager) {
			this.manager = manager;
		}
//		public Collection<Step> getSteps() {
//			return steps;
//		}
//		public void setSteps(Collection<Step> steps) {
//			this.steps = steps;
//		}
		public Long getId() {
			return id;
		}
}

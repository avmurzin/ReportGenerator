package com.avmurzin.report.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name="step")
public class Step {
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Long id;
	
	private String taskId;
	private String step;
	private Long stepUpTime;
	private Long stepDownTime;
	private String worker;
	
//	@ManyToOne
//	private Task task;
	
	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}
	
	public String getStep() {
		return step;
	}

	public void setStep(String step) {
		this.step = step;
	}

	public Long getStepUpTime() {
		return stepUpTime;
	}

	public void setStepUpTime(Long stepUpTime) {
		this.stepUpTime = stepUpTime;
	}

	public Long getStepDownTime() {
		return stepDownTime;
	}

	public void setStepDownTime(Long stepDownTime) {
		this.stepDownTime = stepDownTime;
	}

	public String getWorker() {
		return worker;
	}

	public void setWorker(String worker) {
		this.worker = worker;
	}

//	public Task getTask() {
//		return task;
//	}

//	public void setTask(Task task) {
//		this.task = task;
//	}

	public Long getId() {
		return id;
	}
	
	

}

/**
 * 
 */
package com.varone.web.aggregator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import com.varone.web.aggregator.unit.TimeUnitTransfer;
import com.varone.web.eventlog.bean.SparkEventLogBean;
import com.varone.web.eventlog.bean.SparkEventLogBean.BlockManager;
import com.varone.web.eventlog.bean.SparkEventLogBean.ExecutorAdded;
import com.varone.web.eventlog.bean.SparkEventLogBean.JobEnd;
import com.varone.web.eventlog.bean.SparkEventLogBean.JobStart;
import com.varone.web.eventlog.bean.SparkEventLogBean.StageCompleted;
import com.varone.web.eventlog.bean.SparkEventLogBean.StageInfos;
import com.varone.web.eventlog.bean.SparkEventLogBean.StageSubmit;
import com.varone.web.eventlog.bean.SparkEventLogBean.TaskEnd;
import com.varone.web.eventlog.bean.SparkEventLogBean.TaskStart;
import com.varone.web.eventlog.bean.SparkEventLogBean.TaskEnd.TaskMetrics;
import com.varone.web.metrics.bean.MetricBean;
import com.varone.web.metrics.bean.NodeBean;
import com.varone.web.metrics.bean.TimeValuePairBean;
import com.varone.web.vo.DefaultApplicationVO;
import com.varone.web.vo.DefaultNodeVO;
import com.varone.web.vo.DefaultTotalNodeVO;
import com.varone.web.vo.HistoryDetailStageVO;
import com.varone.web.vo.JobVO;
import com.varone.web.vo.MetricPropVO;
import com.varone.web.vo.StageVO;
import com.varone.web.vo.SummaryExecutorVO;
import com.varone.web.vo.TasksVO;
import com.varone.web.vo.TimeValueVO;

/**
 * @author allen
 *
 */
public class UIDataAggregator {
	
	private TimeUnitTransfer timeUnitTransfer;
	
	/**
	 * 
	 */
	public UIDataAggregator() {
		this.timeUnitTransfer = new TimeUnitTransfer();
	}
	
	
	public DefaultTotalNodeVO aggregateClusterDashBoard(List<String> metrics, int runningAppNum, List<String> periodSparkAppId, 
			List<String> allNodeHost, Map<String, List<NodeBean>> nodeMetricsByAppId, 
			Map<String, SparkEventLogBean> inProgressEventLogByAppId, List<Long> plotPointInPeriod) throws Exception{
		DefaultTotalNodeVO result = new DefaultTotalNodeVO();
		int nodeNum = allNodeHost.size();
		int jobNum  = runningAppNum;
		int taskNum = 0, executorNum = 0;
		
		List<MetricPropVO> metricProps = new ArrayList<MetricPropVO>();
		Map<String, Integer> taskStartedNumByNode = new LinkedHashMap<String, Integer>();
		Map<String, Integer> executorNumByNode = new LinkedHashMap<String, Integer>();
		Map<String, Map<String, List<TimeValueVO>>> propToMetrics = new LinkedHashMap<String, Map<String, List<TimeValueVO>>>();
		
		for(String metric: metrics){
			String[] propertyAndTitle = UIMrtricsPropTransfer.getUIMetricPropertyByMetricName(metric);
			MetricPropVO metricPropVO = new MetricPropVO();
			metricPropVO.setProperty(propertyAndTitle[0]);
			metricPropVO.setTitle(propertyAndTitle[1]);
			metricProps.add(metricPropVO);
			Map<String, List<TimeValueVO>> host2Metrics = new LinkedHashMap<String, List<TimeValueVO>>();
			for(String host: allNodeHost){
				
				List<TimeValueVO> defaultValues = new ArrayList<TimeValueVO>();
				for(Long time: plotPointInPeriod){
					TimeValueVO pair = new TimeValueVO();
					pair.setTime(this.timeUnitTransfer.transferToAxisX(time));
					pair.setValue("0");
					defaultValues.add(pair);
				}
				
				host2Metrics.put(host, defaultValues);
			}
			propToMetrics.put(propertyAndTitle[0], host2Metrics);
		}
		
		for(String host: allNodeHost){
			executorNumByNode.put(host, 0);
			taskStartedNumByNode.put(host, 0);
		}

		
		
		for(Entry<String, SparkEventLogBean> entry: inProgressEventLogByAppId.entrySet()){
			SparkEventLogBean eventLog = entry.getValue();
			taskNum += (eventLog.getTaskStart().size() - eventLog.getTaskEnd().size());
			executorNum += eventLog.getExecutorAdd().size();
			
			for(TaskStart taskStart: eventLog.getTaskStart()){
				int n = taskStartedNumByNode.get(taskStart.getInfo().getHost());
				taskStartedNumByNode.put(taskStart.getInfo().getHost(), ++n);
			}
			
			for(ExecutorAdded executorAdd: eventLog.getExecutorAdd()){
				int n = executorNumByNode.get(executorAdd.getInfo().getHost());
				executorNumByNode.put(executorAdd.getInfo().getHost(), ++n);
			}
		}
		
		for(Entry<String, List<NodeBean>> entry: nodeMetricsByAppId.entrySet()){
			for(NodeBean node: entry.getValue()){
				String host = node.getHost();
				for(MetricBean metric: node.getMetrics()){
					String propertyName = UIMrtricsPropTransfer.getUIMetricPropertyByMetricValue(metric.getName());
					
					Map<String, List<TimeValueVO>> host2Metric = propToMetrics.get(propertyName);
					
					// get a new one
					List<TimeValuePairBean> newValues = metric.getValues();
					// get current value
					List<TimeValueVO> currValues = host2Metric.get(host);
					// aggregate and store
					
					for(TimeValuePairBean newPair: newValues){
						for(TimeValueVO currPair: currValues){
							if(this.timeUnitTransfer.transferToAxisX(
									newPair.getTime()).equals(currPair.getTime())){
								long newValue = Long.parseLong(newPair.getValue());
								long currValue = Long.parseLong(currPair.getValue());
								currPair.setValue(String.valueOf(newValue+currValue));
								break;
							}
						}
					}
				}
			}
		}
		result.setExecutorNumByNode(executorNumByNode);
		result.setExecutorNum(executorNum);
		result.setJobNum(jobNum);
		result.setNodeNum(nodeNum);
		result.setTaskNum(taskNum);
		result.setTaskStartedNumByNode(taskStartedNumByNode);
		result.setPropToMetrics(propToMetrics);
		result.setMetricProps(metricProps);
		return result;
	}
	
	public DefaultApplicationVO aggregateJobDashBoard(List<String> metrics, List<String> allNodeHost, 
			SparkEventLogBean inProgressLog, List<NodeBean> nodeMetrics, List<Long> plotPointInPeriod){
		DefaultApplicationVO result = new DefaultApplicationVO();
		
		List<MetricPropVO> metricProps = new ArrayList<MetricPropVO>();
		Map<String, String> taskStartedNumByNode = new LinkedHashMap<String, String>();
		Map<String, Integer> executorNumByNode = new LinkedHashMap<String, Integer>();
		Map<String, Map<String, List<TimeValueVO>>> propToMetrics = new LinkedHashMap<String, Map<String, List<TimeValueVO>>>();
		
		for(String metric: metrics){
			String[] propertyAndTitle = UIMrtricsPropTransfer.getUIMetricPropertyByMetricName(metric);
			MetricPropVO metricPropVO = new MetricPropVO();
			metricPropVO.setProperty(propertyAndTitle[0]);
			metricPropVO.setTitle(propertyAndTitle[1]);
			metricProps.add(metricPropVO);
			Map<String, List<TimeValueVO>> host2Metrics = new LinkedHashMap<String, List<TimeValueVO>>();
			for(String host: allNodeHost){
				
				List<TimeValueVO> defaultValues = new ArrayList<TimeValueVO>();
				for(Long time: plotPointInPeriod){
					TimeValueVO pair = new TimeValueVO();
					pair.setTime(this.timeUnitTransfer.transferToAxisX(time));
					pair.setValue("0");
					defaultValues.add(pair);
				}
				
				host2Metrics.put(host, defaultValues);
			}
			propToMetrics.put(propertyAndTitle[0], host2Metrics);
		}
		
		for(String host: allNodeHost) executorNumByNode.put(host, 0);
		for(String host: allNodeHost) taskStartedNumByNode.put(host, "0");
		
		result.setExecutorNumByNode(executorNumByNode);
		result.setTaskStartedNumByNode(taskStartedNumByNode);
		
		int executorNum      = inProgressLog.getExecutorAdd().size();
		int taskNum          = 0;
		int completedTaskNum = 0;
		int failedTaskNum    = 0;
						
		for(JobStart jobStart: inProgressLog.getJobStart()){
			for(StageInfos stageInfo: jobStart.getStageInfos()){
				taskNum += stageInfo.getTaskNum();
			}
		}
		
		for(NodeBean node: nodeMetrics){
			String host = node.getHost();
			for(MetricBean metric: node.getMetrics()){
				String propertyName = UIMrtricsPropTransfer.getUIMetricPropertyByMetricValue(metric.getName());
				Map<String, List<TimeValueVO>> host2Metric = propToMetrics.get(propertyName);
				
				// get a new one
				List<TimeValuePairBean> newValues = metric.getValues();
				// get current value
				List<TimeValueVO> currValues = host2Metric.get(host);
				// aggregate and store
				
				for(TimeValuePairBean newPair: newValues){
					for(TimeValueVO currPair: currValues){
						if(this.timeUnitTransfer.transferToAxisX(
								newPair.getTime()).equals(currPair.getTime())){
							long newValue = Long.parseLong(newPair.getValue());
							long currValue = Long.parseLong(currPair.getValue());
							currPair.setValue(String.valueOf(newValue+currValue));
							break;
						}
					}
				}
								
				if(metric.getName().indexOf("completeTasks") != -1){					
					String newestValue = currValues.get(currValues.size()-1).getValue();
					if(currValues.size() > 2 && newestValue.equals("0")){
						newestValue = currValues.get(currValues.size()-2).getValue();
					} 
					taskStartedNumByNode.put(host, newestValue);
					completedTaskNum += Integer.valueOf(newestValue);
				}
					
			}
		}
		
		for(TaskEnd taskEnd: inProgressLog.getTaskEnd()){
			failedTaskNum += taskEnd.getInfo().isFailed()?1:0;
			// calculate the completedTaskNum on here, but it's too slow
//			if(taskEnd.getInfo().isFailed())
//				failedTaskNum++;
//			else
//				completedTaskNum++;
		}
		
		for(ExecutorAdded executorAdded: inProgressLog.getExecutorAdd()){
			int n = executorNumByNode.get(executorAdded.getInfo().getHost());
			executorNumByNode.put(executorAdded.getInfo().getHost(), ++n);
		}
		
		result.setExecutorNum(executorNum);
		result.setTaskNum(taskNum);
		result.setCompletedTaskNum(completedTaskNum);
		result.setFailedTaskNum(failedTaskNum);
		result.setPropToMetrics(propToMetrics);
		result.setMetricProps(metricProps);
		
		return result;
	}


	public DefaultNodeVO aggregateNodeDashBoard(List<String> metrics, String node,
			Map<String, NodeBean> nodeMetricsByAppId, List<Long> plotPointInPeriod) {
		DefaultNodeVO result = new DefaultNodeVO();
		List<MetricPropVO> metricProps = new ArrayList<MetricPropVO>();
		Map<String, List<TimeValueVO>> propToMetrics = new LinkedHashMap<String, List<TimeValueVO>>();
		
		for(String metric: metrics){
			String[] propertyAndTitle = UIMrtricsPropTransfer.getUIMetricPropertyByMetricName(metric);
			MetricPropVO metricPropVO = new MetricPropVO();
			metricPropVO.setProperty(propertyAndTitle[0]);
			metricPropVO.setTitle(propertyAndTitle[1]);
			metricProps.add(metricPropVO);
			
			List<TimeValueVO> defaultValues = new ArrayList<TimeValueVO>();
			for(Long time: plotPointInPeriod){
				TimeValueVO pair = new TimeValueVO();
				pair.setTime(this.timeUnitTransfer.transferToAxisX(time));
				pair.setValue("0");
				defaultValues.add(pair);
			}
			
			propToMetrics.put(propertyAndTitle[0], defaultValues);
		}
		
		for(Entry<String, NodeBean> entry: nodeMetricsByAppId.entrySet()){
			NodeBean nodeBean = entry.getValue();
			for(MetricBean metricBean: nodeBean.getMetrics()){
				String propertyName = UIMrtricsPropTransfer.getUIMetricPropertyByMetricValue(metricBean.getName());
				
				// get a new one
				List<TimeValuePairBean> newValues = metricBean.getValues();
				// get current value
				List<TimeValueVO> currValues = propToMetrics.get(propertyName);
				// aggregate and store
				
				for(TimeValuePairBean newPair: newValues){
					for(TimeValueVO currPair: currValues){
						if(this.timeUnitTransfer.transferToAxisX(
								newPair.getTime()).equals(currPair.getTime())){
							long newValue = Long.parseLong(newPair.getValue());
							long currValue = Long.parseLong(currPair.getValue());
							currPair.setValue(String.valueOf(newValue+currValue));
							break;
						}
					}
				}
			}
		}
		
		result.setPropToMetrics(propToMetrics);
		result.setMetricProps(metricProps);
		return result;
	}


	public List<JobVO> aggregateApplicationJobs(String applicationId,
			SparkEventLogBean eventLog) {
		List<JobVO> result = new ArrayList<JobVO>();
		
		List<JobStart> jobStarts = eventLog.getJobStart();
		List<JobEnd> jobEnds     = eventLog.getJobEnd();
		
		for(JobStart jobStart: jobStarts){
			JobVO job = new JobVO();
			List<SparkEventLogBean.StageInfos> stageInfos = jobStart.getStageInfos();
			if(!stageInfos.isEmpty()){
				job.setDescription(jobStart.getStageInfos().get(0).getName());
			}
			
			int totalTasks  = 0;
			int successTasks = 0;
			int totalStages = jobStart.getStageInfos().size();
			int successStages = 0;
			
			Set<Integer> jobStages = new TreeSet<Integer>();
			for(SparkEventLogBean.StageInfos stageInfo: stageInfos){
				jobStages.add(stageInfo.getId());
				totalTasks+=stageInfo.getTaskNum();
			}
			
			for(TaskEnd taskEnd: eventLog.getTaskEnd()){
				if(jobStages.contains(taskEnd.getStageId()) 
						&& taskEnd.getReason().getReason().equals("Success")){
					successTasks++;
				}
			}
			
			for(StageCompleted stageCompleted: eventLog.getStageComplete()){
				if(jobStages.contains(stageCompleted.getStageInfo().getId()) 
						&& (null == stageCompleted.getStageInfo().getFailureReason() ||
							"".equals(stageCompleted.getStageInfo().getFailureReason()))){
					successStages++;
				}
			}
			String duration = "";
			for(JobEnd jobEnd: jobEnds){
				boolean found = false;
				if(jobStart.getJobId() == jobEnd.getId()){
					found = true;
					duration = String.valueOf(jobEnd.getCompleteTime() - jobStart.getSubmitTime());
					break;
				}
				if(found) break;
			}
			
			job.setDuration(duration);
			job.setSubmitTime(jobStart.getSubmitTime()+"");
			job.setStagesSuccessVSTotal(successStages+"/"+totalStages);
			job.setStagesSuccessPercent((successStages*100/totalStages) + "%");
			job.setTasksSuccessVSTotal(successTasks+"/"+totalTasks);
			job.setTasksSuccessPercent((successTasks*100/totalTasks) + "%");
			job.setId(jobStart.getJobId());
			result.add(job);
		}
		
		return result;
	}


	public List<StageVO> aggregateJobStages(String applicationId, String jobId,
			SparkEventLogBean eventLog) {
		List<StageVO> result = new ArrayList<StageVO>();
		List<Integer> stageIds = new ArrayList<Integer>();
		
		
		for(JobStart jobStart: eventLog.getJobStart()){
			if(String.valueOf(jobStart.getJobId()).equals(jobId)){
				for(StageInfos stageInfo: jobStart.getStageInfos()){
					stageIds.add(stageInfo.getId());
				}
				break;
			}
		}
		
		for(StageSubmit stageSubmit: eventLog.getStageSubmit()){
			StageInfos stageSubmitInfo = stageSubmit.getStageInfo();
			if(stageIds.contains(stageSubmitInfo.getId())){
				StageVO stageVO = new StageVO();
				stageVO.setId(stageSubmitInfo.getId());
				stageVO.setDescription(stageSubmitInfo.getName());
				stageVO.setSubmitTime(String.valueOf(stageSubmitInfo.getSubmitTime()));
				
				for(StageCompleted stageComplete: eventLog.getStageComplete()){
					StageInfos stageCompleteInfo = stageComplete.getStageInfo();
					if(stageSubmitInfo.getId() == stageCompleteInfo.getId()){
						stageVO.setDuration(String.valueOf(
								stageSubmitInfo.getCompleteTime() - stageSubmitInfo.getSubmitTime()));
						int successTasks = 0;
						long readAmount = 0;
						long writeAmount = 0;
						for(TaskEnd taskEnd: eventLog.getTaskEnd()){
							if(stageCompleteInfo.getId() == taskEnd.getStageId() 
									&& taskEnd.getReason().getReason().equals("Success")){
								successTasks++;
								TaskMetrics metrics = taskEnd.getMetrics();
								if(metrics.getInputMetrics() != null)
									readAmount += metrics.getInputMetrics().getReadByte();
								if(metrics.getOutputMetrics() != null)
									writeAmount += metrics.getOutputMetrics().getWriteByte();
							}
						}
						stageVO.setReadAmount(readAmount+"");
						stageVO.setWriteAmount(writeAmount+"");
						stageVO.setTasksSuccessVSTotal(successTasks+"/"+stageSubmitInfo.getTaskNum());
						stageVO.setTasksSuccessPercent((successTasks*100/stageSubmitInfo.getTaskNum()) + "%");
						break;
					}
				}
				result.add(stageVO);
			}
		}
		
		return result;
	}
	
	public HistoryDetailStageVO aggregateHistoryDetialStage(SparkEventLogBean eventLog){
		HistoryDetailStageVO historyStage = new HistoryDetailStageVO();
		
		
		List<TaskStart> taskStartList = eventLog.getTaskStart();
		List<TaskEnd> taskEndList = eventLog.getTaskEnd();
		
		Map<Integer, TaskStart> tempMapTaskStart = new HashMap<Integer, TaskStart>();
		
		Map<String, Integer> totalTasks = new HashMap<String, Integer>();
		Map<String, Integer> failedTotalTasks = new HashMap<String, Integer>();
		Map<String, Integer> succeededTotalTasks = new HashMap<String, Integer>();
		Map<String, Long> taskTotalTaskTime = new HashMap<String, Long>();
		Map<String, Long> inputSizeTotalTasks = new HashMap<String, Long>();
		Map<String, Long> recordTotalTasks = new HashMap<String, Long>();
		
		for(TaskStart taskStart : taskStartList){
			tempMapTaskStart.put(taskStart.getInfo().getIndex(), taskStart);
		}
		
		Map<Integer, TaskEnd> tempMapTaskEnd = new HashMap<Integer, TaskEnd>();
		for(TaskEnd taskEnd : taskEndList){
			tempMapTaskEnd.put(taskEnd.getInfo().getIndex(), taskEnd);
		}
		
		List<TasksVO> taskVOList = new ArrayList<TasksVO>();
		for(int i = 0 ; i < tempMapTaskStart.size(); i++){
			TasksVO taskVO = new TasksVO();
			TaskStart taskStart = tempMapTaskStart.get(i);
			TaskEnd taskEnd = tempMapTaskEnd.get(i);
			if(taskStart != null){
				taskVO.setAttempt(taskStart.getInfo().getAttempt());
				taskVO.setIndex(taskStart.getInfo().getIndex());
				taskVO.setId(taskStart.getInfo().getId());
				taskVO.setLaunchTime(String.valueOf(taskStart.getInfo().getLanuchTime()));
				taskVO.setLocality(taskStart.getInfo().getLocality());
				
				String host = taskStart.getInfo().getHost();
				taskVO.setExecutorIDAndHost(taskStart.getInfo().getExecutorId() + "/" + host);
			
				//sum total tasks
				if(totalTasks.get(host) != null){
					int taskNumber = totalTasks.get(host);
					totalTasks.put(host, taskNumber + 1);
				}else{
					totalTasks.put(host, 1);
				}
				
			
			}
			if(taskEnd != null){
				taskVO.setFinishTime(String.valueOf(taskEnd.getInfo().getFinishTime()));
				taskVO.setGcTime(String.valueOf(taskEnd.getMetrics().getGcTime()));
				taskVO.setResultSize(taskEnd.getMetrics().getResultSize());
				taskVO.setRunTime(taskEnd.getMetrics().getRunTime());
				taskVO.setStatus(taskEnd.getReason().getReason());
				
				//sum task end failed
				if(taskEnd.getInfo().isFailed() == true){
					if(failedTotalTasks.get(taskEnd.getMetrics().getHost()) != null){
						Integer failedNumber = failedTotalTasks.get(taskEnd.getMetrics().getHost());
						failedTotalTasks.put(taskEnd.getMetrics().getHost(), failedNumber + 1);
					}else{
						failedTotalTasks.put(taskEnd.getMetrics().getHost(), 1);
					}
				}
				//sum succeeded task
				if(taskEnd.getReason().getReason().equals("Success")){
					if(succeededTotalTasks.get(taskEnd.getMetrics().getHost()) != null){
						Integer succeededNumber = succeededTotalTasks.get(taskEnd.getMetrics().getHost());
						succeededTotalTasks.put(taskEnd.getMetrics().getHost(), succeededNumber + 1);
					}else{
						succeededTotalTasks.put(taskEnd.getMetrics().getHost(), 1);
					}
				}
				//sum runtime 
				if(taskTotalTaskTime.get(taskEnd.getMetrics().getHost()) != null){
					Long taskTotalTimeNumber = taskTotalTaskTime.get(taskEnd.getMetrics().getHost());
					taskTotalTaskTime.put(taskEnd.getMetrics().getHost(), taskTotalTimeNumber + taskEnd.getMetrics().getRunTime());
				}else{
					taskTotalTaskTime.put(taskEnd.getMetrics().getHost(), taskEnd.getMetrics().getRunTime());
				}
				
			
			}
			if(taskEnd.getMetrics() != null && taskEnd.getMetrics().getInputMetrics() != null){
				taskVO.setInputSizeAndRecords(taskEnd.getMetrics().getInputMetrics().getReadByte() + "/" +
	                      taskEnd.getMetrics().getInputMetrics().getRecordRead());
			
				//sum input size
				if(inputSizeTotalTasks.get(taskEnd.getMetrics().getHost()) != null){
					Long inputSizeTotalNumber = inputSizeTotalTasks.get(taskEnd.getMetrics().getHost());
					inputSizeTotalTasks.put(taskEnd.getMetrics().getHost(), inputSizeTotalNumber + taskEnd.getMetrics().getInputMetrics().getReadByte());
				}else{
					inputSizeTotalTasks.put(taskEnd.getMetrics().getHost(), taskEnd.getMetrics().getInputMetrics().getReadByte());
				}
				//sum records
				if(recordTotalTasks.get(taskEnd.getMetrics().getHost()) != null){
					Long recordTotalNumber = recordTotalTasks.get(taskEnd.getMetrics().getHost());
					recordTotalTasks.put(taskEnd.getMetrics().getHost(), recordTotalNumber + taskEnd.getMetrics().getInputMetrics().getRecordRead());
				}else{
					recordTotalTasks.put(taskEnd.getMetrics().getHost(), taskEnd.getMetrics().getInputMetrics().getRecordRead());
				}
			}
			
			
			taskVOList.add(taskVO);
		}
		
		
		historyStage.setTasks(taskVOList);
		
		
		List<BlockManager> blockManagerList = eventLog.getBlockManager();
		List<SummaryExecutorVO> aggregatorExecutor = new ArrayList<SummaryExecutorVO>();
		for(BlockManager blockManager : blockManagerList){
			SummaryExecutorVO summaryExecutorVO = new SummaryExecutorVO();
			summaryExecutorVO.setAddress(blockManager.getBlockManagerID().getHost() + ":" + blockManager.getBlockManagerID().getPort());
			if(totalTasks.get(blockManager.getBlockManagerID().getHost()) != null){
				Integer resultTotalTasks = totalTasks.get(blockManager.getBlockManagerID().getHost());
				summaryExecutorVO.setTotalTasks(resultTotalTasks);
			}
			if(failedTotalTasks.get(blockManager.getBlockManagerID().getHost()) != null){
				Integer resultFailedTotalTasks = failedTotalTasks.get(blockManager.getBlockManagerID().getHost());
				summaryExecutorVO.setFailedTasks(resultFailedTotalTasks);
			}
			if(succeededTotalTasks.get(blockManager.getBlockManagerID().getHost()) != null){
				Integer resultSucceededTotalTasks = succeededTotalTasks.get(blockManager.getBlockManagerID().getHost());
				summaryExecutorVO.setSucceededTasks(resultSucceededTotalTasks);
			}
			if(taskTotalTaskTime.get(blockManager.getBlockManagerID().getHost()) != null){
				Long resultTaskTotalTaskTime = taskTotalTaskTime.get(blockManager.getBlockManagerID().getHost());
				summaryExecutorVO.setTaskTime(resultTaskTotalTaskTime);
			}
			String inputSizeAndRecords = "";
			if(inputSizeTotalTasks.get(blockManager.getBlockManagerID().getHost()) != null){
				Long inputTotalTask = inputSizeTotalTasks.get(blockManager.getBlockManagerID().getHost());
				inputSizeAndRecords = String.valueOf(inputTotalTask) + "/";
			}
			if(recordTotalTasks.get(blockManager.getBlockManagerID().getHost()) != null){
				Long recordTotalTask = recordTotalTasks.get(blockManager.getBlockManagerID().getHost());
				inputSizeAndRecords = inputSizeAndRecords + recordTotalTask;
			}
			
			summaryExecutorVO.setInputSizeAndrecords(inputSizeAndRecords);
			summaryExecutorVO.setExecuteId(String.valueOf(blockManager.getBlockManagerID().getId()));
			summaryExecutorVO.setMaxMemory(blockManager.getMaxMemory());
			
			aggregatorExecutor.add(summaryExecutorVO);
		}
		historyStage.setAggregatorExecutor(aggregatorExecutor);
		
		
		return historyStage;
	}

}

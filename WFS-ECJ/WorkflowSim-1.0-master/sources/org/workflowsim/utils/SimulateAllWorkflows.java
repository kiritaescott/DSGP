package org.workflowsim.utils;

import ec.app.wfsgp.GPUtility;
import org.workflowsim.Job;
import org.workflowsim.examples.scheduling.GenericSchedulingAlgorithmExample;

import java.io.File;
import java.util.*;

public class SimulateAllWorkflows {
    private static final int TRAINING_SET = 0;
    private static final int TESTING_SET = 1;

    private static Map<String, Double> workflowStats;

    public static void main(String [] args){
        try {
            GPUtility utility = new GPUtility();
            workflowStats = new HashMap<>();

            File[] taskFiles = utility.getTaskFileList(TESTING_SET); // training set
            File[] vmFiles = utility.getVMFileList();

            ArrayList<Double> overallMakeSpans = new ArrayList<>();


            double overallMakespans = 0;
            double overallJobs = 0;

            for (File tf : taskFiles) {// test cases
                String filePath = tf.getPath();
                utility.setDaxPath(filePath);

                for (File vmf : vmFiles) {
                    utility.setVmDaxPath(vmf.getPath());
                    utility.setVmRatiosList();


                    double vmMkspn = 0;
                    double vmJobs = 0;

                    Parameters.SchedulingAlgorithm sch_method = Parameters.SchedulingAlgorithm.HEFT;    // change according to which algorithm to run
                    utility.setAlgorithmParameters(sch_method);

                    List<Job> jobs = new ArrayList<>();

                    jobs = GenericSchedulingAlgorithmExample.runSimulation(utility);

                    vmJobs = jobs.size();
                    vmMkspn = utility.getCloudletsMakeSpan(jobs);

                    overallJobs += vmJobs;
                    overallMakespans += vmMkspn;

                    double avgVmMkspn = vmMkspn / vmJobs;

                    overallMakeSpans.add(avgVmMkspn);

                    workflowStats.put(tf.getName()+"_"+vmf.getName(), avgVmMkspn);
                }

            }

            System.out.println("overallMkspn: " + overallMakespans);

            double overallAvgMkspan = overallMakespans / overallJobs;

            System.out.println("overallAvgMkspn: " + overallAvgMkspan);

            double mean = utility.getAverageMakespan(overallMakeSpans);

            System.out.println("Number of scenarios : "+overallMakeSpans.size());
            System.out.println("Mean of overall makespans : "+mean);

            double sd = utility.getStandardDeviation(overallMakeSpans);

            System.out.println("Standard Deviation: "+sd);


            List sortedKeys=new ArrayList(workflowStats.keySet());
            Collections.sort(sortedKeys);
            Collections.reverse(sortedKeys);

            for (Object objKey : sortedKeys) {
                String key = (String) objKey;
                Double value = workflowStats.get(key);

                System.out.println("The average makespan for : " + key + " is: "+ value);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

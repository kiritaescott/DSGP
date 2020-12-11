package ec.app.wfsgp;

import ec.EvolutionState;
import ec.Individual;
import ec.Problem;
import ec.gp.ADFStack;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.workflowsim.*;
import org.workflowsim.utils.Parameters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;

public class GPUtility {


    private static final int TRAINING_SET = 0;
    private static final int TESTING_SET = 1;

    private static String trainingPath = "/Users/koe/Documents/Gitlab-Thesis/thesis/WFS-ECJ/config/workflows/training";
    private static String testingPath = "/Users/koe/Documents/Gitlab-Thesis/thesis/WFS-ECJ/config/workflows/testing";
    private static String vmPath = "/Users/koe/Documents/Gitlab-Thesis/thesis/WFS-ECJ/config/vms";

    private String daxPath;
    private String vmDaxPath;

    private EvolutionState state;
    private Individual ind;
    private int subpopulation;
    private int threadnum;
    private DoubleData input;
    private ADFStack stack;
    private Problem problem;

    private ArrayList<Double> vm_ratios;

    private Parameters.SchedulingAlgorithm p;

    public Problem getProblem() {
        return problem;
    }

    public void setProblem(Problem problem) {
        this.problem = problem;
    }

    public ADFStack getStack() {
        return stack;
    }

    public void setStack(ADFStack stack) {
        this.stack = stack;
    }

    public DoubleData getInput() {
        return input;
    }

    public void setInput(DoubleData input) {
        this.input = input;
    }

    public EvolutionState getState() {
        return state;
    }

    public void setState(EvolutionState state) {
        this.state = state;
    }

    public Individual getInd() {
        return ind;
    }

    public void setInd(Individual ind) {
        this.ind = ind;
    }

    public int getSubpopulation() {
        return subpopulation;
    }

    public void setSubpopulation(int subpopulation) {
        this.subpopulation = subpopulation;
    }

    public int getThreadnum() {
        return threadnum;
    }

    public void setThreadnum(int threadnum) {
        this.threadnum = threadnum;
    }

    public ArrayList<Double> getVmRatios (){
        return this.vm_ratios;
    }

    /** return the currently set daxPath */
    public String getDaxPath() {
        return daxPath;
    }

    /** set the daxPath */
    public void setDaxPath(String daxPath) {
        this.daxPath = daxPath;
    }

    /** Return the list of files in either the training or testing set */
    public File[] getTaskFileList(int set) {
        String path = set == TRAINING_SET ? trainingPath : testingPath;

        File fd = new File(path);
        return fd.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".xml");
            }
        });
    }

    public void setAlgorithmParameters(Parameters.SchedulingAlgorithm p){
        this.p = p;
    }

    public Parameters.SchedulingAlgorithm getAlgorithmParameters(){
        return this.p;
    }

    public static File[] getVMFileList() {
        File fd = new File(vmPath);
        return fd.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".json");
            }
        });

    }

    public double getExpectedCompletionTime (Cloudlet c, List vms){
        double expectedCompletionTime = 0;

        Job job = (Job) c;
        List <Task> children = job.getChildList();

        for (Task task : children){
            double highestCompletionTime = 0;

            for (Object vmObject : vms){
                CondorVM vm = (CondorVM) vmObject;
                double execTime = task.getCloudletLength() / vm.getMips() ;
                if (execTime > highestCompletionTime){ highestCompletionTime = execTime; }
            }

            expectedCompletionTime += highestCompletionTime;
        }
        return expectedCompletionTime;
    }

    public double getAverageMakespan(ArrayList<Double> makeSpans){
        double mean = 0;
        double sum = 0;

        int length = makeSpans.size();

        for (double num : makeSpans){
            sum += num;
        }

        mean = sum / length;

        return mean;
    }

    public double getStandardDeviation(ArrayList<Double> makeSpans){
        double mean = getAverageMakespan(makeSpans);
        double standardDeviation = 0;

        int length = makeSpans.size();

        for (double num : makeSpans){
            standardDeviation += Math.pow(num - mean, 2);
        }

        double sd = Math.sqrt(standardDeviation/length);

        return sd;
    }


    public CondorVM getVMWithMinFitness(List<CondorVM> vms) {
        Collections.sort(vms, new Comparator<CondorVM>() {
            @Override
            public int compare(CondorVM v1, CondorVM v2) {
                return Double.compare(v1.getFitnessValue(), v2.getFitnessValue());
            }
        });
        for (int i = 0; i < vms.size(); i++){
            if (vms.get(i).getState() == WorkflowSimTags.VM_STATUS_IDLE){
                return vms.get(i);
            }
        }
        return null;
    }


    public double getCloudletsMakeSpan(List<Job> jobs) { //
        //list is already ordered so not necessary to calculate max and min
         return jobs.get(jobs.size()-1).getFinishTime() - jobs.get(0).getExecStartTime();   // ignore first job
    }

    /** return the number of children a certain cloudlet has */
    public int getCloudletNumOfChildren(Cloudlet c){
        int children = 0;

        Job j = (Job) c;

        for (Task t: j.getTaskList()){
            children += t.getChildList().size();
        }

        return children;
    }

    /** order the list of cloudlets in priority based on the number of children they have */
    public void orderCloudletsByNumOfChildren(ArrayList<Cloudlet> p) {
        p.sort((lp, rp) -> {

            int lp_children = getCloudletNumOfChildren(lp);
            int rp_children = getCloudletNumOfChildren(rp);

            return rp_children - lp_children; // sort by descending size
        });

    }

    public void setVmRatiosList() { // vm list in one file
        this.vm_ratios = new ArrayList<>();

        try {
            JSONParser parser = new JSONParser();
            byte[] encoded = Files.readAllBytes(Paths.get(this.vmDaxPath));
            String content = new String(encoded, StandardCharsets.UTF_8);

            Object obj_vm = parser.parse(content);
            JSONObject jsonObject = (JSONObject) obj_vm;

            JSONArray vms = (JSONArray) jsonObject.get("virtualmachine");
            Iterator<Object> iterator_vms = vms.iterator();


            while (iterator_vms.hasNext()) {
                Object it = iterator_vms.next();
                JSONObject data = (JSONObject) it;

                String name = (String) data.get("name");

                double ratio = Double.valueOf(data.get("ratio").toString());

                this.vm_ratios.add(ratio);
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**Prints the job objects
     * @param list list of jobs */
    public void printJobList(List<Job> list) {
        int size = list.size();
        Job job;

        String indent = "    ";
        Log.printLine();
        Log.printLine("========== OUTPUT ==========");
        Log.printLine("Cloudlet ID" + indent + "STATUS" + indent
                + "Data center ID" + indent + "VM ID" + indent + indent + "Time" + indent
                + "Start Time" + indent + "Finish Time" + indent + "Depth" + indent + "Cost");

        DecimalFormat dft = new DecimalFormat("###.##");
        double cost = 0.0;
        double time = 0.0;
        for (int i = 0; i < size; i++) {
            job = list.get(i);
            Log.print(indent + job.getCloudletId() + indent + indent);
            Log.print(indent + job.getCloudletId() + indent + indent);
//            if (job.getClassType() == Parameters.ClassType.STAGE_IN.value) {
//                Log.print("Stage-in");
//            }

            cost += job.getProcessingCost();
            time += job.getActualCPUTime();
            if (job.getCloudletStatus() == Cloudlet.SUCCESS) {
                //Log.print("SUCCESS");
                Log.printLine(indent + indent + job.getResourceId() + indent + indent + indent + job.getVmId()
                        + indent + indent + indent + dft.format(job.getActualCPUTime())
                        + indent + indent + dft.format(job.getExecStartTime()) + indent + indent + indent
                        + dft.format(job.getFinishTime()) + indent + indent + indent + job.getDepth()
                        + indent + indent + indent + dft.format(job.getProcessingCost()));
            } else if (job.getCloudletStatus() == Cloudlet.FAILED) {
                Log.print("FAILED");
                Log.printLine(indent + indent + job.getResourceId() + indent + indent + indent + job.getVmId()
                        + indent + indent + indent + dft.format(job.getActualCPUTime())
                        + indent + indent + dft.format(job.getExecStartTime()) + indent + indent + indent
                        + dft.format(job.getFinishTime()) + indent + indent + indent + job.getDepth()
                        + indent + indent + indent + dft.format(job.getProcessingCost()));
            }
        }

    }

    public void setVmDaxPath(String path) {
        this.vmDaxPath = path;
    }

    public String getVmDaxPath(){ return this.vmDaxPath; }
}

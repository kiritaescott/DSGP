package ec.app.wfsgp;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.Log;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.workflowsim.*;
import org.workflowsim.utils.Parameters;
import org.workflowsim.utils.ReplicaCatalog;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public class Utility {
    private static final int TRAINING_SET = 0;
    private static final int TESTING_SET = 1;

    private static String trainingPath = "/Users/koe/Documents/Gitlab-Thesis/thesis/WFS-ECJ/WFSP_Grid_Files/config/workflows/training";
    private static String testingPath = "/Users/koe/Documents/Gitlab-Thesis/thesis/WFS-ECJ/WFSP_Grid_Files/config/workflows/testing";
    //  private static String vmPath = "/Users/koe/Desktop/Summer Project/2018-2019_Summer_Project/2018-2019_Summer_Project/Code_for_Summer_Project/src/ec/app/tutorial4/config/vm";

    private int userId;

    private int jobIdStartsFrom;
    private int idIndex;


    private Map<String, Task> mName2Task;
    private Map mTask2Job;

    public static final String PARAMS_FILE = "/Users/koe/Documents/Gitlab-Thesis/thesis/WFS-ECJ/WorkflowSim-1.0-master/sources/org/workflowsim/ecj/ec/app/wfsgp/wfsgp.params";

    private List<Task> taskList;
    private List<Cloudlet> cloudletList;
    private List <Job> jobList;
    private List<CondorVM> vmList;
    private List<FileItem> allFileList;

    public Utility (){
        this.taskList = new ArrayList<>();
        this.allFileList = new ArrayList<>();
        this.jobList = new ArrayList<>();
        this.cloudletList = new ArrayList<>();
        this.vmList = new ArrayList<>();
        this.mName2Task = new HashMap<>();
        this.mTask2Job = new HashMap<>();
    }


    public List getCloudlets(String filePath) {
        parseXmlFile(filePath);
        setJobList();
        setCloudletList(this.jobList);

        return this.cloudletList;
    }

    private void setJobList() {
        this.getTask2Job().clear();
        for (Task task : getTaskList()) {
            List<Task> list = new ArrayList<>();
            list.add(task);
            Job job = addTasks2Job(list);
            job.setVmId(task.getVmId());
            getTask2Job().put(task, job);
        }
        /**
         * Handle the dependencies issue.
         */
        updateDependencies();
    }

    public List<Job> getJobList() {
        return this.jobList;
    }

    /**
     * Gets the job list.
     *
     * @return the job list
     */
    public List getCloudletList() {
        return this.cloudletList;
    }

    public void setCloudletList(List <? extends Cloudlet> list) {
        this.cloudletList = new ArrayList<>(list);
    }

    public final Map getTask2Job() {
        return this.mTask2Job;
    }

    /**
     * Gets the task list
     *
     * @return the task list
     */
    @SuppressWarnings("unchecked")
    public List<Task> getTaskList() {
        return this.taskList;
    }

    /**
     * Gets the vm list
     *
     * @return the vm list
     */
    public List getVmList() {
        return this.vmList;
    }

    public static Queue<Cloudlet> setCloudletPriorityQueue(List <Cloudlet> cloudlets){
        LinkedList<Cloudlet> orderedCloudlets = new LinkedList<>();

        ArrayList<Cloudlet> parents = new ArrayList<>(); //parent

        for (Cloudlet cloud : cloudlets) { // all cloudlets are entry cloudlets so add them to parents list
            parents.add(cloud);
        }

        while (orderedCloudlets.size() < cloudlets.size()){
            // child = new ArrayList<Cloudlet>();
            orderCloudletsByNumOfChildren(parents);       // can change this out for orderCloudletsByNumOfChildren later

            for (Cloudlet cloudlet: parents){
                if (!orderedCloudlets.contains(cloudlet)){
                    orderedCloudlets.add(cloudlet);
                }
            }

        }

        return orderedCloudlets;
    }

    private static void orderCloudletsByNumOfChildren(ArrayList<Cloudlet> p) {
        p.sort((lp, rp) -> {

            int lp_children = getCloudletNumOfChildren(lp);
            int rp_children = getCloudletNumOfChildren(rp);

            return rp_children - lp_children; // sort by descending size
        });

    }

    public static int getCloudletNumOfChildren(Cloudlet c){
        int children = 0;

        Job j = (Job) c;

        for (Task t: j.getTaskList()){
            children += t.getChildList().size();
        }

        return children;
    }

    private static void orderCloudletsBySize(ArrayList<Cloudlet> p) {
        p.sort((lp, rp) -> {
            return (int)rp.getCloudletLength() - (int)lp.getCloudletLength(); // sort by descending size
        });

    }

    public static double getMaxFinishTime(List<Cloudlet> cs) { // max finish time of cloudlet list
        if (cs.size() > 0) {
            double[] ft = new double[cs.size()];
            for (int i = 0; i < cs.size(); i++) {
                ft[i] = cs.get(i).getFinishTime();
            }

            Arrays.sort(ft);
            return ft[ft.length - 1];
        } else
            return 0;
    }

    public static double costOfExecutingOnVm(CondorVM vm, Cloudlet c){
        double cost = 0;

        cost = (vm.getCostPerBW() * c.getCloudletFileSize()) + (vm.getCostPerBW() * c.getCloudletOutputSize());
//        System.out.println("Cost of executing: "+cost+" from cost per bw: "+vm.getCostPerBW()+" * cloud file size "+c.getCloudletFileSize());
//        System.out.println("+ cost per bw "+vm.getCostPerBW()+" * cloudlet output size: "+c.getCloudletOutputSize());

        return cost;
    }


    public static File[] getTaskFileList(int set) {
        String path = set == TRAINING_SET ? trainingPath : testingPath;

        File fd = new File(path);
        return fd.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".xml");
            }
        });
    }

    protected static List<CondorVM> getVms(int userId, int vms) {

        //Creates a container to store VMs. This list is passed to the broker later
        LinkedList<CondorVM> list = new LinkedList<>();
        //VM Parameters
        long size = 10000; //image size (MB)
        int ram = 512; //vm memory (MB)
        int mips = 1000;
        long bw = 1000;
        int pesNumber = 1; //number of cpus
        String vmm = "Xen"; //VMM name

        //create VMs
        CondorVM[] vm = new CondorVM[vms];
        double cost = 3.0;              // the cost of using processing in this resource
        double costPerMem = 0.05;		// the cost of using memory in this resource
        double costPerStorage = 0.1;	// the cost of using storage in this resource
        double costPerBw = 0.1;			// the cost of using bw in this resource

        for (int i = 0; i < vms; i++) {
            double ratio = 1.0;
            if (i == 0) {
                ratio = 0.923157115686327;
            } else if (i == 1) {
                ratio = 0.9785439788010425;
            } else if (i == 2) {
                ratio = 0.11844081508286086;
            } else if (i == 3) {
                ratio = 0.9779063368009382;
            } else if (i == 4) {
                ratio = 0.5325349521385307;
            }

            vm[i] = new CondorVM(i, userId, mips * ratio, pesNumber, ram, (long) (bw * ratio), size, vmm,
                    (cost * ratio), (costPerMem * ratio), (costPerStorage * ratio), (costPerBw * ratio), new CloudletSchedulerSpaceShared());
            list.add(vm[i]);
        }
        return list;
    }


    /**
     * Sets the depth of a task
     *
     * @param task the task
     * @param depth the depth
     */
    private void setDepth(Task task, int depth) {
        if (depth > task.getDepth()) {
            task.setDepth(depth);
        }
        for (Task cTask : task.getChildList()) {
            setDepth(cTask, task.getDepth() + 1);
        }
    }

    /**
     * Parse a file with jdom
     */
    private void parseXmlFile(String path) {

        try {

            SAXBuilder builder = new SAXBuilder();
            //parse using builder to get DOM representation of the XML file
            Document dom = builder.build(new File(path));
            Element root = dom.getRootElement();
            List<Element> list = root.getChildren();
            for (Element node : list) {
                switch (node.getName().toLowerCase()) {
                    case "job":
                        long length = 0;
                        String nodeName = node.getAttributeValue("id");
                        String nodeType = node.getAttributeValue("name");
                        /**
                         * capture runtime. If not exist, by default the runtime
                         * is 0.1. Otherwise CloudSim would ignore this task.
                         * BUG/#11
                         */
                        double runtime;
                        if (node.getAttributeValue("runtime") != null) {
                            String nodeTime = node.getAttributeValue("runtime");
                            runtime = 1000 * Double.parseDouble(nodeTime);
                            if (runtime < 100) {
                                runtime = 100;
                            }
                            length = (long) runtime;
                        } else {
                            Log.printLine("Cannot find runtime for " + nodeName + ",set it to be 0");
                        }   //multiple the scale, by default it is 1.0
                        length *= Parameters.getRuntimeScale();
                        List<Element> fileList = node.getChildren();
                        List<FileItem> mFileList = new ArrayList<>();
                        for (Element file : fileList) {
                            if (file.getName().toLowerCase().equals("uses")) {
                                String fileName = file.getAttributeValue("name");//DAX version 3.3
                                if (fileName == null) {
                                    fileName = file.getAttributeValue("file");//DAX version 3.0
                                }
                                if (fileName == null) {
                                    Log.print("Error in parsing xml");
                                }

                                String inout = file.getAttributeValue("link");
                                double size = 0.0;

                                String fileSize = file.getAttributeValue("size");
                                if (fileSize != null) {
                                    size = Double.parseDouble(fileSize) /*/ 1024*/;
                                } else {
                                    Log.printLine("File Size not found for " + fileName);
                                }

                                /**
                                 * a bug of cloudsim, size 0 causes a problem. 1
                                 * is ok.
                                 */
                                if (size == 0) {
                                    size++;
                                }
                                /**
                                 * Sets the file type 1 is input 2 is output
                                 */
                                Parameters.FileType type = Parameters.FileType.NONE;
                                switch (inout) {
                                    case "input":
                                        type = Parameters.FileType.INPUT;
                                        break;
                                    case "output":
                                        type = Parameters.FileType.OUTPUT;
                                        break;
                                    default:
                                        Log.printLine("Parsing Error");
                                        break;
                                }
                                FileItem tFile;
                                /*
                                 * Already exists an input file (forget output file)
                                 */
                                if (size < 0) {
                                    /*
                                     * Assuming it is a parsing error
                                     */
                                    size = 0 - size;
                                    Log.printLine("Size is negative, I assume it is a parser error");
                                }
                                /*
                                 * Note that CloudSim use size as MB, in this case we use it as Byte
                                 */
                                if (type == Parameters.FileType.OUTPUT) {
                                    /**
                                     * It is good that CloudSim does tell
                                     * whether a size is zero
                                     */
                                    tFile = new FileItem(fileName, size);
                                } else if (ReplicaCatalog.containsFile(fileName)) {
                                    tFile = ReplicaCatalog.getFile(fileName);
                                } else {

                                    tFile = new FileItem(fileName, size);
                                    ReplicaCatalog.setFile(fileName, tFile);
                                }

                                tFile.setType(type);
                                mFileList.add(tFile);

                            }
                        }
                        Task task;
                        //In case of multiple workflow submission. Make sure the jobIdStartsFrom is consistent.
                        synchronized (this) {
                            task = new Task(this.jobIdStartsFrom, length);
                            this.jobIdStartsFrom++;
                        }
                        task.setType(nodeType);
                        task.setUserId(userId);
                        mName2Task.put(nodeName, task);
                        for (FileItem file : mFileList) {
                            task.addRequiredFile(file.getName());
                        }
                        task.setFileList(mFileList);
                        this.getTaskList().add(task);

                        /**
                         * Add dependencies info.
                         */
                        break;
                    case "child":
                        List<Element> pList = node.getChildren();
                        String childName = node.getAttributeValue("ref");
                        if (mName2Task.containsKey(childName)) {

                            Task childTask = (Task) mName2Task.get(childName);

                            for (Element parent : pList) {
                                String parentName = parent.getAttributeValue("ref");
                                if (mName2Task.containsKey(parentName)) {
                                    Task parentTask = (Task) mName2Task.get(parentName);
                                    parentTask.addChild(childTask);
                                    childTask.addParent(parentTask);
                                }
                            }
                        }
                        break;
                }
            }
            /**
             * If a task has no parent, then it is root task.
             */
            ArrayList roots = new ArrayList<>();
            for (Task task : mName2Task.values()) {
                task.setDepth(0);
                if (task.getParentList().isEmpty()) {
                    roots.add(task);
                }
            }

            /**
             * Add depth from top to bottom.
             */
            for (Iterator it = roots.iterator(); it.hasNext();) {
                Task task = (Task) it.next();
                setDepth(task, 1);
            }
            /**
             * Clean them so as to save memory. Parsing workflow may take much
             * memory
             */
            this.mName2Task.clear();

        } catch (JDOMException jde) {
            Log.printLine("JDOM Exception;Please make sure your dax file is valid");

        } catch (IOException ioe) {
            Log.printLine("IO Exception;Please make sure dax.path is correctly set in your config file");

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Parsing Exception");
        }
    }

    protected final Job addTasks2Job(List<Task> taskList) {
        if (taskList != null && !taskList.isEmpty()) {
            int length = 0;

            int userId = 0;
            int priority = 0;
            int depth = 0;
            /// a bug of cloudsim makes it final of input file size and output file size
            Job job = new Job(idIndex, length/*, inputFileSize, outputFileSize*/);
            job.setClassType(Parameters.ClassType.COMPUTE.value);
            for (Task task : taskList) {
                length += task.getCloudletLength();

                userId = task.getUserId();
                priority = task.getPriority();
                depth = task.getDepth();
                List<FileItem> fileList = task.getFileList();
                job.getTaskList().add(task);

                getTask2Job().put(task, job);
                for (FileItem file : fileList) {
                    boolean hasFile = job.getFileList().contains(file);
                    if (!hasFile) {
                        job.getFileList().add(file);
                        if (file.getType() == Parameters.FileType.INPUT) {
                            //for stag-in jobs to be used
                            if (!this.allFileList.contains(file)) {
                                this.allFileList.add(file);
                            }
                        } else if (file.getType() == Parameters.FileType.OUTPUT) {
                            this.allFileList.add(file);
                        }
                    }
                }
                for (String fileName : task.getRequiredFiles()) {
                    if (!job.getRequiredFiles().contains(fileName)) {
                        job.getRequiredFiles().add(fileName);
                    }
                }
            }

            job.setCloudletLength(length);
            job.setUserId(userId);
            job.setDepth(depth);
            job.setPriority(priority);

            idIndex++;
            getJobList().add(job);
            return job;
        }

        return null;
    }

    /**
     * Update the dependency issues between tasks/jobs
     */
    protected final void updateDependencies() {
        for (Task task : getTaskList()) {
            Job job = (Job) getTask2Job().get(task);
            for (Task parentTask : task.getParentList()) {
                Job parentJob = (Job) getTask2Job().get(parentTask);
                if (!job.getParentList().contains(parentJob) && parentJob != job) {//avoid dublicate
                    job.addParent(parentJob);
                }
            }
            for (Task childTask : task.getChildList()) {
                Job childJob = (Job) getTask2Job().get(childTask);
                if (!job.getChildList().contains(childJob) && childJob != job) {//avoid dublicate
                    job.addChild(childJob);
                }
            }
        }
        getTask2Job().clear();
        getTaskList().clear();
    }

    /**
     * Prints the job objects
     *
     * @param list list of jobs
     */
    protected static void printJobList(List<Job> list) {
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

            cost += job.getProcessingCost();
            time += job.getActualCPUTime();
            if (job.getCloudletStatus() == Cloudlet.SUCCESS) {
                Log.print("SUCCESS");
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
        Log.printLine("The total cost is " + dft.format(cost));
        Log.printLine("The total time is " + dft.format(time));
        Log.printLine("The average cost is "+dft.format(cost/size));
        Log.printLine("The average time is "+dft.format(time/size));
    }

}

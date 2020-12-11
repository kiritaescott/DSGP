package org.workflowsim.examples.scheduling;

import ec.app.wfsgp.GPUtility;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.workflowsim.*;
import org.workflowsim.utils.ClusteringParameters;
import org.workflowsim.utils.OverheadParameters;
import org.workflowsim.utils.Parameters;
import org.workflowsim.utils.ReplicaCatalog;

import java.io.File;
import java.util.*;

public class GenericSchedulingAlgorithmExample {

        ////////////////////////// STATIC METHODS ///////////////////////
        /**
         * Creates main() to run this example This example has only one datacenter
         * and one storage
         */
        public static List<Job> runSimulation(GPUtility gpUtility) {

            try {
                // First step: Initialize the WorkflowSim package.

                int vmNum = 10;//number of vms; ... likely to be passed in as a parameter in future
                /**
                 * Should change this based on real physical path
                 */
                String daxPath = gpUtility.getDaxPath();

                File daxFile = new File(daxPath);
                if (!daxFile.exists()) {
                    Log.printLine("Warning: Please replace daxPath with the physical path in your working environment!");
                    return null;
                }

                /**
                 * Since we are using HEFT planning algorithm, the scheduling
                 * algorithm should be static such that the scheduler would not
                 * override the result of the planner
                 */
                Parameters.SchedulingAlgorithm sch_method = gpUtility.getAlgorithmParameters();
                Parameters.PlanningAlgorithm pln_method = Parameters.PlanningAlgorithm.INVALID;
                ReplicaCatalog.FileSystem file_system = ReplicaCatalog.FileSystem.LOCAL;

                /**
                 * No overheads
                 */
                OverheadParameters op = new OverheadParameters(0, null, null, null, null, 0);

                /**
                 * No Clustering
                 */
                ClusteringParameters.ClusteringMethod method = ClusteringParameters.ClusteringMethod.NONE;
                ClusteringParameters cp = new ClusteringParameters(0, 0, method, null);

                /**
                 * Initialize static parameters
                 */
                Parameters.init(vmNum, daxPath, null,
                        null, op, cp, sch_method, pln_method,
                        null, 0, gpUtility);
                ReplicaCatalog.init(file_system);

                // before creating any entities.
                int num_user = 1;   // number of grid users
                Calendar calendar = Calendar.getInstance();
                boolean trace_flag = false;  // mean trace events

                // Initialize the CloudSim library
                CloudSim.init(num_user, calendar, trace_flag);

                WorkflowDatacenter datacenter0 = createDatacenter("Datacenter_0");

                /**
                 * Create a WorkflowPlanner with one schedulers.
                 */
                WorkflowPlanner wfPlanner = new WorkflowPlanner("planner_0", 1);
                /**
                 * Create a WorkflowEngine.
                 */
                WorkflowEngine wfEngine = wfPlanner.getWorkflowEngine();
                /**
                 * Create a list of VMs.The userId of a vm is basically the id of
                 * the scheduler that controls this vm.
                 */
                //List<AmazonVM> vmlist0 = gpUtility.getVMList(wfEngine.getSchedulerId(0), gpUtility.getVmDaxPath());

                List<CondorVM> vmlist0 = createVM(wfEngine.getSchedulerId(0), gpUtility.getVmRatios());
                /**
                 * Submits this list of vms to this WorkflowEngine.
                 */
                wfEngine.submitVmList(vmlist0, 0);

                /**
                 * Binds the data centers with the scheduler.
                 */
                wfEngine.bindSchedulerDatacenter(datacenter0.getId(), 0);

                CloudSim.startSimulation();
                List<Job> outputList0 = wfEngine.getJobsReceivedList();
                CloudSim.stopSimulation();

                /**return the output list back to GP*/
                return outputList0;
            } catch (Exception e) {
                Log.printLine("The simulation has been terminated due to an unexpected error");
            }
            return null;    // should never get here
        }

    protected static List<CondorVM> createVM(int userId, ArrayList<Double> ratios) {

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
        CondorVM[] vm = new CondorVM[ratios.size()];

        for (int i = 0; i < ratios.size(); i++) {
            double ratio = ratios.get(i);
            vm[i] = new CondorVM(i, userId, mips * ratio, pesNumber, ram, bw, size, vmm, new CloudletSchedulerSpaceShared());
            list.add(vm[i]);
        }

        return list;
    }


        protected static WorkflowDatacenter createDatacenter(String name) {

            // Here are the steps needed to create a PowerDatacenter:
            // 1. We need to create a list to store one or more
            //    Machines
            List<Host> hostList = new ArrayList<>();

            // 2. A Machine contains one or more PEs or CPUs/Cores. Therefore, should
            //    create a list to store these PEs before creating
            //    a Machine.
            for (int i = 1; i <= 20; i++)   {
                List<Pe> peList1 = new ArrayList<>();
                int mips = 2000;
                // 3. Create PEs and add these into the list.
                //for a quad-core machine, a list of 4 PEs is required:
                peList1.add(new Pe(0, new PeProvisionerSimple(mips))); // need to store Pe id and MIPS Rating
                peList1.add(new Pe(1, new PeProvisionerSimple(mips)));

                int hostId = 0;
                int ram = 2048; //host memory (MB)
                long storage = 1000000; //host storage
                int bw = 10000;
                hostList.add(
                        new Host(
                                hostId,
                                new RamProvisionerSimple(ram),
                                new BwProvisionerSimple(bw),
                                storage,
                                peList1,
                                new VmSchedulerTimeShared(peList1))); // This is our first machine
                //hostId++;
            }

            // 4. Create a DatacenterCharacteristics object that stores the
            //    properties of a data center: architecture, OS, list of
            //    Machines, allocation policy: time- or space-shared, time zone
            //    and its price (G$/Pe time unit).
            String arch = "x86";      // system architecture
            String os = "Linux";          // operating system
            String vmm = "Xen";
            double time_zone = 10.0;         // time zone this resource located
            double cost = 3.0;              // the cost of using processing in this resource
            double costPerMem = 0.05;		// the cost of using memory in this resource
            double costPerStorage = 0.1;	// the cost of using storage in this resource
            double costPerBw = 0.1;			// the cost of using bw in this resource
            LinkedList<Storage> storageList = new LinkedList<>();	//we are not adding SAN devices by now
            WorkflowDatacenter datacenter = null;

            DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                    arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);

            // 5. Finally, we need to create a storage object.
            /**
             * The bandwidth within a data center in MB/s.
             */
            int maxTransferRate = 15;// the number comes from the futuregrid site, you can specify your bw

            try {
                // Here we set the bandwidth to be 15MB/s
                HarddriveStorage s1 = new HarddriveStorage(name, 1e12);
                s1.setMaxTransferRate(maxTransferRate);
                storageList.add(s1);
                datacenter = new WorkflowDatacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return datacenter;
        }



}

package org.workflowsim.scheduling;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.Log;
import org.workflowsim.*;
import org.workflowsim.planning.HEFTPlanningAlgorithm;
import org.workflowsim.utils.Parameters;

import java.util.*;

/**
 * Heft algorithm. Copied from the MinMinScheduling Algorithm
 *
 * @author Kirita-Rose Escott
 * @since WorkflowSim Toolkit 1.0
 * @date Sep 9, 2019
 */

public class HeftSchedulingAlgorithm extends BaseSchedulingAlgorithm  {

    private Map<Cloudlet, Map<CondorVM, Double>> computationCosts;
    private Map<Cloudlet, Map<Cloudlet, Double>> transferCosts;
    private Map<Cloudlet, Double> rank;
    private Map<CondorVM, List<Event>> schedules;
    private Map<Cloudlet, Double> earliestFinishTimes;
    private double averageBandwidth;
    private List<CloudletRank> cloudletRank;

    private class Event {

        public double start;
        public double finish;

        public Event(double start, double finish) {
            this.start = start;
            this.finish = finish;
        }
    }

    private class CloudletRank implements Comparable<CloudletRank> {

        public Cloudlet cloudlet;
        public Double rank;

        public CloudletRank(Cloudlet cloudlet, Double rank) {
            this.cloudlet = cloudlet;
            this.rank = rank;
        }

        @Override
        public int compareTo(CloudletRank o) {
            return o.rank.compareTo(rank);
        }
    }

    public HeftSchedulingAlgorithm()  {
        computationCosts = new HashMap<>();
        transferCosts = new HashMap<>();
        rank = new HashMap<>();
        earliestFinishTimes = new HashMap<>();
        schedules = new HashMap<>();
        cloudletRank = new ArrayList<>();
    }


    @Override
    public void run() throws Exception {

        int cloudletSize = getCloudletList().size();
        int vmSize = getVmList().size();

        if (cloudletSize == 0){
            return;
        }

        averageBandwidth = calculateAverageBandwidth();

        for (Object vmObject : getVmList()) {
            CondorVM vm = (CondorVM) vmObject;
            schedules.put(vm, new ArrayList<>());
        }

        // Prioritization phase
        calculateComputationCosts();
        //calculateTransferCosts();
        calculateRanks();

        // Selection phase
        allocateCloudlets();

        for (CloudletRank rank : cloudletRank) {
            Cloudlet cloudlet = rank.cloudlet;
            for (int j = 0; j < vmSize; j++) {
                CondorVM vm = (CondorVM) getVmList().get(j);
                if (vm.getState() == WorkflowSimTags.VM_STATUS_IDLE && vm.getId() == cloudlet.getVmId()) {
                    vm.setState(WorkflowSimTags.VM_STATUS_BUSY);
                    cloudlet.setVmId(vm.getId());
                    getScheduledList().add(cloudlet);
                    break;
                }
            }
        }
    }

    /**
     * Calculates the average available bandwidth among all VMs in Mbit/s
     *
     * @return Average available bandwidth in Mbit/s
     */
    private double calculateAverageBandwidth() {
        double avg = 0.0;
        for (Object vmObject : getVmList()) {
            CondorVM vm = (CondorVM) vmObject;
            avg += vm.getBw();
        }
        return avg / getVmList().size();
    }

    /**
     * Populates the computationCosts field with the time in seconds to compute
     * a task in a vm.
     */
    private void calculateComputationCosts() {
        for (Object cloudletObject : getCloudletList()) {
            Cloudlet cloudlet = (Cloudlet) cloudletObject;
            Map<CondorVM, Double> costsVm = new HashMap<>();
            for (Object vmObject : getVmList()) {
                CondorVM vm = (CondorVM) vmObject;
                if (vm.getNumberOfPes() < cloudlet.getNumberOfPes()) {
                    costsVm.put(vm, Double.MAX_VALUE);
                } else {
                    costsVm.put(vm,
                            cloudlet.getCloudletTotalLength() / vm.getMips());
                }
            }
            computationCosts.put(cloudlet, costsVm);
        }
    }

    /**
     * Populates the transferCosts map with the time in seconds to transfer all
     * files from each parent to each child
     */
    private void calculateTransferCosts() {
        // Initializing the matrix
        for (Object cloudletObj1 : getCloudletList()){
            Cloudlet cloudlet1 = (Cloudlet) cloudletObj1;
            Map<Cloudlet, Double> cloudletTransferCosts = new HashMap<>();
            for (Object cloudletObj2 : getCloudletList()){
                Cloudlet cloudlet2 = (Cloudlet) cloudletObj2;
                cloudletTransferCosts.put(cloudlet2, 0.0);
            }
            transferCosts.put(cloudlet1, cloudletTransferCosts);
        }

        // Calculating the actual values
        for (Object parentObj : getCloudletList()) {
            Cloudlet parent = (Cloudlet) parentObj;
            Job parentJob = (Job) parentObj;
            for (Task task : parentJob.getChildList()){
                Cloudlet child = null;
                for (Object childObj: getCloudletList()){
                    Cloudlet childCloudlet = (Cloudlet) childObj;
                    if (task.getCloudletId()==childCloudlet.getCloudletId()){
                        child = childCloudlet;
                        break;
                    }
                }
                if (child == null){
                    break;
                }
                transferCosts.get(parent).put(child,  calculateTransferCost(parent, child));
            }
        }
    }

    /**
     * Accounts the time in seconds necessary to transfer all files described
     * between parent and child
     *
     * @param parent
     * @param child
     * @return Transfer cost in seconds
     */
    private double calculateTransferCost(Cloudlet parent, Cloudlet child) {
        Job parentJob = (Job) parent;
        Job childJob = (Job) child;

        /**
         * In our experiments a Job object's task list will only ever have one Task
         * This is due to NOT making use of clustering in the Clustering Engine
         * Should that change we will need to modify our implementation of HEFT
         * */
        List<FileItem> parentFiles = parentJob.getTaskList().get(0).getFileList();
        List<FileItem> childFiles = childJob.getTaskList().get(0).getFileList();

        double acc = 0.0;

        for (FileItem parentFile : parentFiles) {
            if (parentFile.getType() != Parameters.FileType.OUTPUT) {
                continue;
            }

            for (FileItem childFile : childFiles) {
                if (childFile.getType() == Parameters.FileType.INPUT
                        && childFile.getName().equals(parentFile.getName())) {
                    acc += childFile.getSize();
                    break;
                }
            }
        }

        //file Size is in Bytes, acc in MB
        acc = acc / Consts.MILLION;
        // acc in MB, averageBandwidth in Mb/s
        return acc * 8 / averageBandwidth;
    }

    /**
     * Invokes calculateRank for each task to be scheduled
     */
    private void calculateRanks() {
        for (Object cloudletObj : getCloudletList()) {
            Cloudlet cloudlet = (Cloudlet) cloudletObj;
            calculateRank(cloudlet);
        }
    }

    /**
     * Populates rank.get(task) with the rank of task as defined in the HEFT
     * paper.
     *
     * @param cloudlet The task have the rank calculates
     * @return The rank
     */
    private double calculateRank(Cloudlet cloudlet) {
        if (rank.containsKey(cloudlet)) {
            return rank.get(cloudlet);
        }

        double averageComputationCost = 0.0;

        for (Double cost : computationCosts.get(cloudlet).values()) {
            averageComputationCost += cost;
        }

        averageComputationCost /= computationCosts.get(cloudlet).size();

        double max = 0.0;

//        Job parent = (Job) cloudlet;
//        for (Task taskChild : parent.getChildList()){
//            for (Object cloudletChildObj: getCloudletList()){
//                Cloudlet child = (Cloudlet) cloudletChildObj;
//                if (child.getCloudletId()==taskChild.getCloudletId()){
//                    double childCost = transferCosts.get(cloudlet).get(child)
//                            + calculateRank(child);
//                    max = Math.max(max, childCost);
//                }
//            }
//
//        }

        rank.put(cloudlet, averageComputationCost + max);

        return rank.get(cloudlet);
    }

    /**
     * Allocates all tasks to be scheduled in non-ascending order of schedule.
     */
    private void allocateCloudlets() {
        for (Cloudlet cloudlet : rank.keySet()) {
            cloudletRank.add(new CloudletRank(cloudlet, rank.get(cloudlet)));
        }

        // Sorting in descending order of rank
        Collections.sort(cloudletRank);
        for (CloudletRank rank : cloudletRank) {
            allocateCloudlet(rank.cloudlet);
        }

    }


    /**
     * Schedules the task given in one of the VMs minimizing the earliest finish
     * time
     *
     * @param task The task to be scheduled
     * @pre All parent tasks are already scheduled
     */
    private void allocateCloudlet(Cloudlet cloudlet) {
        CondorVM chosenVM = null;
        double earliestFinishTime = Double.MAX_VALUE;
        double bestReadyTime = 0.0;
        double finishTime;

        for (Object vmObject : getVmList()) {
            CondorVM vm = (CondorVM) vmObject;
            double minReadyTime = 0.0;

//            for (Cloudlet parent : getParentCloudletParentList(cloudlet)){
//                double readyTime = earliestFinishTimes.get(parent);
//                if (parent.getVmId() != vm.getId()) {
//                    readyTime += transferCosts.get(parent).get(cloudlet);
//                }
//                minReadyTime = Math.max(minReadyTime, readyTime);
//            }

            finishTime = findFinishTime(cloudlet, vm, minReadyTime, false);

            if (finishTime < earliestFinishTime) {
                bestReadyTime = minReadyTime;
                earliestFinishTime = finishTime;
                chosenVM = vm;
            }
        }

        findFinishTime(cloudlet, chosenVM, bestReadyTime, true);
        earliestFinishTimes.put(cloudlet, earliestFinishTime);

        cloudlet.setVmId(chosenVM.getId());
    }

    /**
     * Finds the best time slot available to minimize the finish time of the
     * given task in the vm with the constraint of not scheduling it before
     * readyTime. If occupySlot is true, reserves the time slot in the schedule.
     *
     * @param task The task to have the time slot reserved
     * @param vm The vm that will execute the task
     * @param readyTime The first moment that the task is available to be
     * scheduled
     * @param occupySlot If true, reserves the time slot in the schedule.
     * @return The minimal finish time of the task in the vmn
     */
    private double findFinishTime(Cloudlet cloudlet, CondorVM vm, double readyTime,
                                  boolean occupySlot) {
        List<Event> sched = schedules.get(vm);
        double computationCost = computationCosts.get(cloudlet).get(vm);
        double start, finish;
        int pos;

        if (sched.isEmpty()) {
            if (occupySlot) {
                sched.add(new Event(readyTime, readyTime + computationCost));
            }
            return readyTime + computationCost;
        }

        if (sched.size() == 1) {
            if (readyTime >= sched.get(0).finish) {
                pos = 1;
                start = readyTime;
            } else if (readyTime + computationCost <= sched.get(0).start) {
                pos = 0;
                start = readyTime;
            } else {
                pos = 1;
                start = sched.get(0).finish;
            }

            if (occupySlot) {
                sched.add(pos, new Event(start, start + computationCost));
            }
            return start + computationCost;
        }

        // Trivial case: Start after the latest task scheduled
        start = Math.max(readyTime, sched.get(sched.size() - 1).finish);
        finish = start + computationCost;
        int i = sched.size() - 1;
        int j = sched.size() - 2;
        pos = i + 1;
        while (j >= 0) {
            Event current = sched.get(i);
            Event previous = sched.get(j);

            if (readyTime > previous.finish) {
                if (readyTime + computationCost <= current.start) {
                    start = readyTime;
                    finish = readyTime + computationCost;
                }

                break;
            }
            if (previous.finish + computationCost <= current.start) {
                start = previous.finish;
                finish = previous.finish + computationCost;
                pos = i;
            }
            i--;
            j--;
        }

        if (readyTime + computationCost <= sched.get(0).start) {
            pos = 0;
            start = readyTime;

            if (occupySlot) {
                sched.add(pos, new Event(start, start + computationCost));
            }
            return start + computationCost;
        }
        if (occupySlot) {
            sched.add(pos, new Event(start, finish));
        }
        return finish;
    }
}

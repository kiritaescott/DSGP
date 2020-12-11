package org.workflowsim;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Vm;

public class AmazonVM extends Vm{

    /*
     * The state of a vm. It should be either WorkflowSimTags.VM_STATUS_IDLE
     * or VM_STATUS_READY (not used in workflowsim) or VM_STATUS_BUSY
     */
    private int state;

    /**
     * the cost of using memory in this resource
     */
    private double costPerMem = 0.0;

    /**
     * the cost of using bandwidth in this resource
     */
    private double costPerBW = 0.0;

    /**
     * the cost of using storage in this resource
     */
    private double costPerStorage = 0.0;

    /**
     * the cost of using CPU in this resource
     */
    private double cost = 0;

    /** WFSGP Parameters */
    private double fitnessValue;

    private Cloudlet cloudletToExecute;

    private double executionCost;

    private String name;

    /**
     * Creates a new CondorVM object.
     *
     * @param id unique ID of the VM
     * @param userId ID of the VM's owner
     * @param mips the mips
     * @param numberOfPes amount of CPUs
     * @param ram amount of ram
     * @param bw amount of bandwidth
     * @param size amount of storage
     * @param vmm virtual machine monitor
     * @param cloudletScheduler cloudletScheduler policy for cloudlets
     * @pre id >= 0
     * @pre userId >= 0
     * @pre size > 0
     * @pre ram > 0
     * @pre bw > 0
     * @pre cpus > 0
     * @pre priority >= 0
     * @pre cloudletScheduler != null
     * @post $none
     */
    public AmazonVM(
            int id,
            int userId,
            double mips,
            int numberOfPes,
            int ram,
            long bw,
            long size,
            String vmm,
            CloudletScheduler cloudletScheduler) {
        super(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler);
        /*
         * At the beginning all vm status is idle.
         */
        setState(WorkflowSimTags.VM_STATUS_IDLE);
    }

    /**
     * Creates a new CondorVM object.
     *
     * @param id unique ID of the VM
     * @param userId ID of the VM's owner
     * @param mips the mips
     * @param numberOfPes amount of CPUs
     * @param ram amount of ram
     * @param bw amount of bandwidth
     * @param size amount of storage
     * @param vmm virtual machine monitor
     * @param cost cost for CPU
     * @param costPerBW cost for using bandwidth
     * @param costPerStorage cost for using storage
     * @param costPerMem cost for using memory
     * @param cloudletScheduler cloudletScheduler policy for cloudlets
     * @pre id >= 0
     * @pre userId >= 0
     * @pre size > 0
     * @pre ram > 0
     * @pre bw > 0
     * @pre cpus > 0
     * @pre priority >= 0
     * @pre cloudletScheduler != null
     * @post $none
     */
    public AmazonVM(
            int id,
            int userId,
            String name,
            double mips,
            int numberOfPes,
            int ram,
            long bw,
            long size,
            String vmm,
            double cost,
            double costPerMem,
            double costPerStorage,
            double costPerBW,
            CloudletScheduler cloudletScheduler) {
        this(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler);
        this.name = name;
        this.cost = cost;
        this.costPerBW = costPerBW;
        this.costPerMem = costPerMem;
        this.costPerStorage = costPerStorage;
    }

    /**
     * Gets the CPU cost
     *
     * @return the cost
     */
    public double getCost() {
        return this.cost;
    }

    /**
     * Gets the cost per bw
     *
     * @return the costPerBW
     */
    public double getCostPerBW() {
        return this.costPerBW;
    }

    /**
     * Gets the cost per storage
     *
     * @return the costPerStorage
     */
    public double getCostPerStorage() {
        return this.costPerStorage;
    }

    /**
     * Gets the cost per memory
     *
     * @return the costPerMem
     */
    public double getCostPerMem() {
        return this.costPerMem;
    }

    /**
     * Sets the state of the task
     *
     * @param tag
     */
    public final void setState(int tag) {
        this.state = tag;
    }

    /**
     * Gets the state of the task
     *
     * @return the state of the task
     * @pre $none
     * @post $none
     */
    public final int getState() {
        return this.state;
    }

    /** WFSGP Methods */

    public final void setFitnessValue (double value){
        this.fitnessValue = value;
    }

    public final double getFitnessValue(){
        return this.fitnessValue;
    }

    public final void setCloudletToExecute (Cloudlet cloudletToExecute){ this.cloudletToExecute = cloudletToExecute; }

    public final Cloudlet getCloudletToExecute(){ return this.cloudletToExecute; }

    public final void setExecutionCost(double executionCost){ this.executionCost = executionCost; }

    public final double getExecutionCost(){ return this.executionCost; }
}

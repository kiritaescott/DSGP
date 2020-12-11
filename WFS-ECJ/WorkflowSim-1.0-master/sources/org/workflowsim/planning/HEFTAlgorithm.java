//package org.workflowsim.planning;
//
//import org.fsgpsim.Task;
//import org.fsgpsim.Utility;
//import org.fsgpsim.VirtualMachine;
//
//import java.util.*;
//
//
//public class HEFTAlgorithm {
//
//    public HEFTAlgorithm() {
//        // TODO Auto-generated constructor stub
//    }
//
//    public ArrayList<Object> taskMapping(ArrayList<Task> parentTasks, ArrayList<VirtualMachine> vms, Task t, int j) {
//        ArrayList<Object> updatedVals = new ArrayList<Object>();
//
//        t.setAllocation_time(Utility.getMaxFinishTime(parentTasks));
//
//        for (VirtualMachine vm : vms) {
//            t.setExe_time(t.getTask_size() / (double) vm.getVelocity());
//
//            double preFinishTime = Utility.getMaxFinishTime(vm.getPriority_queue());
//
//            t.setStart_time(Utility.getMaxStartTime(preFinishTime, t.getAllocation_time()));
//            t.setWaiting_time();
//            t.setRelative_finish_time();
//            vm.setRelativeFinish_time(t.getRelative_finish_time());
//        }
//
//        VirtualMachine vmSel = getVMWithMinRFT(vms);
//
//        t.setExe_time(t.getTask_size() / (double) vmSel.getVelocity());
//
//        double preFinishTime = Utility.getMaxFinishTime(vmSel.getPriority_queue());
//
//        t.setStart_time(Utility.getMaxStartTime(preFinishTime, t.getAllocation_time()));
//        t.setWaiting_time();
//        t.setRelative_finish_time();
//        t.setFinish_time();
//
//        vmSel.setPriority_queue(t);
//
//        updatedVals.add(t);
//        updatedVals.add(vmSel);
//
//        return updatedVals;
//    }
//
//
//    private VirtualMachine getVMWithMinRFT(ArrayList<VirtualMachine> vms) {
//        Collections.sort(vms, new Comparator<VirtualMachine>() {
//            @Override
//            public int compare(VirtualMachine v1, VirtualMachine v2) {
//                return Double.compare(v1.getRelativeFinish_time(), v2.getRelativeFinish_time());
//            }
//        });
//
//        return vms.get(0);
//    }
//
//}

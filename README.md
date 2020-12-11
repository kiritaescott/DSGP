# DSGP
Dynamic Workflow Scheduling Genetic Programming

This repository includes the code related to the paper: Genetic Programming Based Hyper Heuristic Approach for Dynamic Workflow Scheduling in the Cloud

In this repository, it includes the code for ECJ and the WorkflowSim simulator, the datasets used, and the paper itself.

The code part is included in the folder named WFS-ECJ. The code is using ECJ 25 (Java) The library of this project is also included in the folder named "jar" under the src folder.

The code for GP is in sources->ec->app->wfsgp. The terminal set and function set of this GP algorithm is included in wfsgp aswell. The parameter files you might want to change to tune the GP are wfsgp.params, koza.params, simple.params and ec.params. To make changes to logic for training and testing you will need to change the MultivaluedRegression.java file. 

The datasets used in the paper are in the WFS-ECJ->config folders. There is one for training workflows, one for testing workflows and one for vms. There are additional workflows that weren't used in Workflowsim-1.0-master->config that may also be used. Note that the larger workflows of 1000 tasks, particularly Sipht can take longer to run. I would suggest taking them out of the training and testing folders while you are learning how to use the code.

In wfsgp->GPUtility class, you will need to update the trainingPath, testingPath and cmPath variables to match your local repository location. 

To run the DSGP project overall, you need to import the whole package and add all the libraries in the package. I was using intellj, if you want to run it in intellj you need to configure the setting for "Run", you need to select java application, then set the main class to ec.Evolve, and set the arguments to "-file wfsgp.params". 

To run the experiments for the competing algorithms, in sources->org->workflowsim->utils there is a runnable class called SimulateAllWorkflows. On line 42 of SimulateAllWorkflows.java you can change the Paramters.SchedulinhAlgorithm.HEFT to the scheduling algorithm you want to run. The implemented algorithms are in sources->org->workflowsim->scheduling. 


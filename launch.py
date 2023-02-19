import os
import sys
import subprocess

# arguments are: path to Net2Plan-CLI.jar directory, topology file, class file, start number of demands, increment number of demands, percentage of traffic generated by CORE nodes, singleTransponder, number of iterations per number of demands, [output file]
if len(sys.argv) < 9:
    print("Missing parameters\n")
    print("Usage: python launch.py <path to Net2Plan-CLI.jar> <topology file> <class file> <start number of demands> <increment number of demands> <percentage of traffic generated by CORE nodes> <singleTransponder> <number of iterations per number of demands> [<output file>]")
    sys.exit(1)
elif len(sys.argv) == 9:
    print("Missing output path\n")
    # if ./results folder does not exist, create it
    if not os.path.exists("./results"):
        os.makedirs("./results")
    output_folder = os.getcwd() + "/results"
elif len(sys.argv) == 10:
    # if output folder does not exist, create it
    if not os.path.exists(sys.argv[9]):
        os.makedirs(sys.argv[9])
    output_folder = sys.argv[9]
else:
    print("Too many parameters\n")
    print("Usage: python launch.py <path to Net2Plan-CLI.jar> <topology file> <class file> <start number of demands> <increment number of demands> <percentage of traffic generated by CORE nodes> <singleTransponder> <number of iterations per number of demands> [<output file>]")
    sys.exit(1)

# get arguments
net2plan_cli_path = sys.argv[1] + "/Net2Plan-CLI.jar"
topology_file = sys.argv[2]
class_file = sys.argv[3]
start_num_demands = int(sys.argv[4])
increment_num_demands = int(sys.argv[5])
percentage_core = float(sys.argv[6])
singleTransponder = sys.argv[7].lower() in ['true', '1', 't', 'y', 'yes']
num_iterations = int(sys.argv[8])

# Output folder structure:
# ./results | sys.argv[9]
#   /singleTransponder | multipleTransponders
#       /C<percentage_core>
#           /demands<num_demands>

if singleTransponder:
    output_folder += "/singleTransponder"
else:
    output_folder += "/multipleTransponders"

# create folder for the current type of transponder
if not os.path.exists(output_folder):
    os.makedirs(output_folder)

output_folder += "/C" + str(percentage_core)
# create folder for the current percentage of traffic generated by CORE nodes
if not os.path.exists(output_folder):
    os.makedirs(output_folder)

print("Output path: " + output_folder + "\n")

# extract class name from class file
class_name = class_file.split("/")[-1].split(".")[0]

# run num iterations for each number of demands
# stop condition is when the execution is stopped for all iterations
all_iterations_failed = False
num_demands = start_num_demands
while(not all_iterations_failed):
    all_iterations_failed = True
    # create folder for the current number of demands
    output_folder_demand = output_folder + "/demands" + str(num_demands)
    if not os.path.exists(output_folder_demand):
        os.makedirs(output_folder_demand)
    #print("folder: "+ output_folder_demand)
    for iteration in range(num_iterations):
        print("Running " + str(num_demands) + " demands, iteration " + str(iteration))
        result = subprocess.run(["/usr/lib/jvm/java-8-openjdk-amd64/bin/java", "-jar", net2plan_cli_path, "--mode", "net-design", "--input-file", topology_file, "--output-file", "output.n2p", "--class-file", class_file, "--class-name", class_name, "--alg-param", "NumberOfDemands="+str(num_demands), "--alg-param", "k=5", "--alg-param", "maxPropagationDelayMs=-1.0", "--alg-param", "numFrequencySlotsPerFiber=4950", "--alg-param", "percentageOfCoreTraffic="+str(percentage_core), "--alg-param", "resultPath="+output_folder_demand, "--alg-param", "singleTransponderForAll="+str(singleTransponder).lower(), "--alg-param", "singleTransponderType=true"], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        # if the execution finished successfully, then at least one iteration was successful

        print(result.stdout.decode("utf-8"))

        if ("Algorithm finished successfully") in result.stdout.decode("utf-8"):
            print("\tExecution successful")
            all_iterations_failed = False
        else:
            print("\tExecution failed")
    if not all_iterations_failed:
        num_demands += increment_num_demands


# print at which number of demands the execution stopped
print("Execution stopped at " + str(num_demands) + " demands")
import os
import sys
import subprocess

from xml.dom import minidom
# check if ./results folder exists
output_folder = os.getcwd() + "/results"

if not os.path.exists(output_folder):
    print(output_folder)
    print("results folder missing")
    sys.exit(1)

# for over directories in results (singleTransponder or multipleTransponder)
for subdir in next(os.walk(output_folder))[1]:
    print(subdir)
    path_transponder = output_folder + '/' + subdir
    # for over directories in singleTransponder or multipleTransponder (C<percentage_core>)
    for case in next(os.walk(path_transponder))[1]:
        # for over directories in C<percentage_core> (demands<num_demands>)
        print('\t' + case)
        for demand in next(os.walk(path_transponder + '/' + case))[1]:
            print('\t\t' + demand)
            # for over files in demands<num_demands> (results<num_demands>)
            for file in next(os.walk(path_transponder + '/' + case + '/' + demand))[2]:
                print('\t\t\t' + file)
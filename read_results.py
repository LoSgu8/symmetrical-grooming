import os
import sys
import pandas as pd

# check if ./results folder exists
output_folder = os.getcwd() + "/results"

if not os.path.exists(output_folder):
    print(output_folder)
    print("results folder missing")
    sys.exit(1)

# create empty pandas dataframe to store results
results = pd.DataFrame()

print("File tree")
# for over directories in results (singleTransponder or multipleTransponder)
for subdir in next(os.walk(output_folder))[1]:
    print('\t'+subdir)
    path_transponder = output_folder + '/' + subdir
    # for over directories in singleTransponder or multipleTransponder (C<percentage_core>)
    for case in next(os.walk(path_transponder))[1]:
        # for over directories in C<percentage_core> (demands<num_demands>)
        print('\t\t' + case)
        for demand in next(os.walk(path_transponder + '/' + case))[1]:
            print('\t\t\t' + demand)
            # for over files in demands<num_demands> (results<num_demands>)
            for file in next(os.walk(path_transponder + '/' + case + '/' + demand))[2]:
                print('\t\t\t\t' + file)
                if file.endswith('.xml'):
                    # read xml file
                    df = pd.read_xml(path_transponder + '/' + case + '/' + demand + '/' + file)
                    # append to results dataframe using concatenation
                    results = pd.concat([results, df], ignore_index=True)

# print rows having 'demands' equal to 350
print(results)



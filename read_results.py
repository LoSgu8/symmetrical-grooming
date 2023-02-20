import os
import sys
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

# --- Read files ---

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

print("\n")

# print results
print(results)

results.sort_values(by=['demands'], inplace=True)

# Split results in single transponder and multiple transponder
results_single = results.loc[results['single_transponder_for_all'] == True]
results_multiple = results.loc[results['single_transponder_for_all'] == False]

# Island size initialization
island_size = [5, 8, 13, 9, 2, 6, 10, 3, 6]

# --- Plot results ---

## PLOT 1: average cost per demand
plt.figure()
# single transponder: number of demands ordered by increasing number of demands
x_single = results_single['demands'].unique()
# single transponder: average cost per demand ordered by increasing number of demands
y_single = results_single.groupby(['demands'])['total_Cost'].mean()
# multiple transponder: number of demands ordered by increasing number of demands
x_multiple = results_multiple['demands'].unique()
# multiple transponder: average cost per demand ordered by increasing number of demands
y_multiple = results_multiple.groupby(['demands'])['total_Cost'].mean()
# plot single transponder
plt.plot(x_single, y_single, linewidth=2.0)
# plot multiple transponder
plt.plot(x_multiple, y_multiple, linewidth=2.0)
plt.xlabel('number of demands')
plt.ylabel('average cost')
plt.title('Average cost per demand')
plt.legend(['single transponder', 'multiple transponder'])
# set xticks to be the same for both plots equal to the union of the two sets
plt.xticks(np.union1d(x_single, x_multiple))
plt.grid(True)
plt.show()

## PLOT 2: average number of transponders per number of demands
fig, axs = plt.subplots(2, 1)
fig.suptitle('Average number of transponder per number of demands', fontsize=16)
# multiple transponder
demands = results_multiple['demands'].unique()
str_demands = [str(x) for x in demands]
x = np.arange(len(str_demands))
yZR_multiple= results_multiple.groupby(['demands'])['number_ZR'].mean()
ZRstd = results_multiple.groupby(['demands'])['number_ZR'].std()
yLR_multiple = results_multiple.groupby(['demands'])['number_LR'].mean()
LRstd = results_multiple.groupby(['demands'])['number_LR'].std()
total_std = np.sqrt(ZRstd**2 + LRstd**2)
width = 0.35
axs[0].bar(x, yZR_multiple+yLR_multiple, width*2, yerr=total_std, capsize=10, label='total', alpha=0.2)
axs[0].bar(x - width/2, yZR_multiple, width, yerr=ZRstd, capsize=5, label='ZR')
axs[0].bar(x + width/2, yLR_multiple, width, yerr=LRstd, capsize=5, label='LR')
axs[0].set_xticks(x, str_demands)
axs[0].set_title('Multiple transponder')
axs[0].set_xlabel('number of demands')
axs[0].set_ylabel('average number of transponders')
axs[0].grid(True)
axs[0].legend()

# single transponder
yZR_single = results_single.groupby(['demands'])['number_ZR'].mean()
yLR_single = results_single.groupby(['demands'])['number_LR'].mean()
# fill yZR and yLR with zeros to have the same length of x
yZR_single = np.append(yZR_single, np.zeros(len(x)-len(yZR_single)))
yLR_single = np.append(yLR_single, np.zeros(len(x)-len(yLR_single)))
axs[1].bar(str_demands, yZR_single+yLR_single, width*2, alpha=0.2, label="total")
axs[1].set_title('Single transponder')
axs[1].set_xlabel('number of demands')
axs[1].set_ylabel('average number of transponders')
axs[1].grid(True)

# same y limits for both plots equal to the maximum of the two sets
y_max = max(max(yZR_single+yLR_single)+100, max(yZR_multiple+yLR_multiple)+100)
axs[0].set_ylim(0, y_max)
axs[1].set_ylim(0, y_max)

plt.show()

# PLOT 3: average (# of transponders/(# of demands*# of nodes)) per island
plt.figure()
island_labels = ['Island ' + str(x) for x in range(1, 10)]
number_of_transponder_per_island_attribute_name = ['Transponder_Island' + str(x) for x in range(1, 10)]
x = np.arange(len(island_labels))
width = 0.35
# single transponder
print(results_single[number_of_transponder_per_island_attribute_name[0]])
print(results_single[number_of_transponder_per_island_attribute_name[0]].sum())

# find the average number of transponders per island/number of demands
y_single = np.zeros(9)
for i in range(9):
    y_single[i] = results_single[number_of_transponder_per_island_attribute_name[i]].sum()/results_single['demands'].sum()
    y_single[i] = y_single[i]/island_size[i]
plt.bar(x-width/2, y_single, width, label="single")

# multiple transponder
# find the average number of transponders per island/number of demands
y_multiple = np.zeros(9)
for i in range(9):
    y_multiple[i] = results_multiple[number_of_transponder_per_island_attribute_name[i]].sum()/results_multiple['demands'].sum()
    y_multiple[i] = y_multiple[i]/island_size[i]

plt.bar(x+width/2, y_multiple, width, label="multiple")

plt.xticks(x, island_labels)
plt.title('Average number of transponders per demand and per node in each island')
plt.ylabel('# of transponders')
plt.grid(True)
plt.legend()
plt.show()


# PLOT 4: number of transponders per node
plt.figure()
# selects all the columns whose name starts with 'ZR_Node', extract in a list the string after 'ZR_Node'
node_labels = [x[7:] for x in results_multiple.columns if x.startswith('ZR_Node')]



# for each node in node_labels find the average number of transponders per node given by 'ZR_Node<node>'+'LR_Node<node>'
yZR_multiple = np.zeros(len(node_labels))
yLR_multiple = np.zeros(len(node_labels))
yZR_single = np.zeros(len(node_labels))
yLR_single = np.zeros(len(node_labels))

average_number_of_transponders_per_node_multiple = np.zeros(len(node_labels))
average_number_of_transponders_per_node_single = np.zeros(len(node_labels))

for i in range(len(node_labels)):
    yZR_multiple[i] = results_multiple['ZR_Node' + node_labels[i]].sum()
    yLR_multiple[i] = results_multiple['LR_Node' + node_labels[i]].sum()
    yZR_single[i] = results_single['ZR_Node' + node_labels[i]].sum()
    yLR_single[i] = results_single['LR_Node' + node_labels[i]].sum()
    average_number_of_transponders_per_node_multiple[i] = (yZR_multiple[i] + yLR_multiple[i])/results_multiple.shape[0]
    average_number_of_transponders_per_node_single[i] = (yZR_single[i] + yLR_single[i])/results_single.shape[0]

# replace '-' with ' ' in node_labels elements
node_labels = [x.replace('-', ' ') for x in node_labels]
# plot a bar plot, each node having two bars, one for single transponder and one for multiple transponder
x = np.arange(len(node_labels))
width = 0.35
plt.bar(x-width/2, average_number_of_transponders_per_node_single, width, label="single")
plt.bar(x+width/2, average_number_of_transponders_per_node_multiple, width, label="multiple")
plt.xticks(x, node_labels, rotation=90)
plt.title('Average number of transponders per node')
plt.ylabel('# of transponders')
plt.legend()
plt.grid(True)
plt.show()




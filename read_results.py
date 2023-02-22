import os
import sys
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import matplotlib.patheffects as pe

# --- Read files ---

# check if ./results folder exists
output_folder = os.getcwd() + "/results"

if not os.path.exists(output_folder):
    print(output_folder)
    print("results folder missing")
    sys.exit(1)

# check if './results/results.pkl' exists
if os.path.exists(output_folder + '/results.pkl'):
    # read pickle file
    results = pd.read_pickle('./results/results.pkl')
    print("Pickle file found")
else:
    print("Pickle file not found")

    # Loop over files in ./results

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

    # Save results to pickle file
    results.to_pickle('./results/results.pkl')

# print results
print(results)

results.sort_values(by=['demands'], inplace=True)

# Split results in single transponder and multiple transponder
results_single = results.loc[results['single_transponder_for_all'] == True]
results_multiple = results.loc[results['single_transponder_for_all'] == False]

# Island size initialization
island_size = [5, 8, 13, 9, 2, 6, 10, 3, 6]

# --- Plot results ---

# -- PLOT 1: average cost per demand
plt.figure(num=1)
# extract list of number of demands
list_num_demands_single = results_single['demands'].unique()
list_num_demands_multiple = results_multiple['demands'].unique()

# list_num_demands is the list_num_demands_<single|multiple> with the biggest length
list_num_demands = list_num_demands_multiple if len(list_num_demands_multiple) > len(list_num_demands_single) \
    else list_num_demands_single

# extract average cost per demand in both the cases
cost_single = results_single.groupby(['demands'])['total_Cost'].mean()
cost_multiple = results_multiple.groupby(['demands'])['total_Cost'].mean()

# plot single transponder
plt.plot(list_num_demands[:len(cost_single)], cost_single, linewidth=2.0)
plt.plot(list_num_demands[:len(cost_multiple)], cost_multiple, linewidth=2.0)
plt.xlabel('number of demands')
plt.ylabel('average cost')
plt.title('Average cost per demand')
plt.legend(['single transponder', 'multiple transponder'])
plt.xticks(list_num_demands, rotation=90)
plt.grid(True)
plt.show()

# -- PLOT 2: average number of transponders and average cost per number of demands
fig, ax = plt.subplots(num=2)
fig.suptitle('Average number of transponder per number of demands', fontsize=16)
# multiple transponder
demands_multiple = results_multiple['demands'].unique()
str_demands_multiple = [str(x) for x in demands_multiple]
x_multiple = np.arange(len(str_demands_multiple))
demands_single = results_single['demands'].unique()
str_demands_single = [str(x) for x in demands_single]
x_single = np.arange(len(str_demands_single))

# x is the x_<single|multiple> with the biggest length
x = x_multiple if len(x_multiple) > len(x_single) else x_single

transponder_multiple = results_multiple.groupby(['demands'])['number_ZR'].mean() \
                       + results_multiple.groupby(['demands'])['number_LR'].mean()
transponder_single = results_single.groupby(['demands'])['number_ZR'].mean() \
                     + results_single.groupby(['demands'])['number_LR'].mean()
width = 0.35

ax.bar((x - width/2)[:len(transponder_multiple)], transponder_multiple, width, label='transponder_multiple')
ax.bar((x + width/2)[:len(transponder_single)], transponder_single, width, label='transponder_single')

ax.set_xticks(x_multiple, str_demands_multiple, rotation=90)
# limit x axis to min(x_multiple.max(), x_single.max())
ax.set_xlim(-1, min(x_multiple.max(), x_single.max())+10.5)

ax.set_title('nb transponders and cost per number of demands')
ax.set_xlabel('number of demands')
ax.set_ylabel('average number of transponders')

cost_axis = ax.twinx()
# plot with line width 2 and edge width 0.5
cost_axis.plot(x[:len(cost_multiple)], cost_multiple, label='cost_multiple', linewidth=2,
               path_effects=[pe.Stroke(linewidth=5, foreground='black'), pe.Normal()])

cost_axis.plot(x[:len(cost_single)], cost_single, label='cost_single', linewidth=2,
               path_effects=[pe.Stroke(linewidth=5, foreground='black'), pe.Normal()])
cost_axis.set_ylabel('average cost', color='red')
cost_axis.tick_params(axis='y', labelcolor='red')

# set y axis axs[0] and cost_axis_multiple limit to be max(y_multiple, yZR_multiple+yLR_multiple)
ax.set_ylim(0, max(cost_multiple.max(), transponder_multiple.max()))
cost_axis.set_ylim(0, max(cost_multiple.max(), transponder_multiple.max()))

ax.grid(True)
ax.legend(loc='upper left')
cost_axis.legend(loc='upper right')

plt.show()


# -- PLOT 3: average number of transponders per number of demands with error bars
fig, axs = plt.subplots(2, 1, num=3)
fig.suptitle('Average number of transponder per number of demands', fontsize=16)
# multiple transponder
demands = results_multiple['demands'].unique()
str_demands = [str(x) for x in demands]
x = np.arange(len(str_demands))
yZR_multiple = results_multiple.groupby(['demands'])['number_ZR'].mean()
ZRstd = results_multiple.groupby(['demands'])['number_ZR'].std()
yLR_multiple = results_multiple.groupby(['demands'])['number_LR'].mean()
LRstd = results_multiple.groupby(['demands'])['number_LR'].std()
total_std = np.sqrt(ZRstd**2 + LRstd**2)
width = 0.35
axs[0].bar(x, transponder_multiple, width*2, yerr=total_std, capsize=5, label='total', alpha=0.2)
axs[0].bar(x - width/2, yZR_multiple, width, yerr=ZRstd, capsize=2, label='ZR')
axs[0].bar(x + width/2, yLR_multiple, width, yerr=LRstd, capsize=2, label='LR')
axs[0].set_xticks(x, str_demands, rotation=90)
axs[0].set_title('Multiple transponder')
axs[0].set_xlabel('number of demands')
axs[0].set_ylabel('average number of transponders')
axs[0].grid(True)
axs[0].legend()

# single transponder
ZRstd_single = results_single.groupby(['demands'])['number_ZR'].std()
LRstd_single = results_single.groupby(['demands'])['number_LR'].std()
total_std_single = np.sqrt(ZRstd_single**2 + LRstd_single**2)

axs[1].bar(str_demands[:len(transponder_single)], transponder_single, width*2, yerr=total_std_single, capsize=5,
           alpha=0.2, label="total")
axs[1].set_xticks(x, str_demands, rotation=90)
axs[1].set_title('Single transponder')
axs[1].set_xlabel('number of demands')
axs[1].set_ylabel('average number of transponders')
axs[1].grid(True)

# same y limits for both plots equal to the maximum of the two sets
y_max = max(max(transponder_single)+100, max(transponder_multiple)+100)
axs[0].set_ylim(0, y_max)
axs[1].set_ylim(0, y_max)

# same x limits for both subplots
axs[0].set_xlim(-1, len(str_demands)+0.5)
axs[1].set_xlim(-1, len(str_demands)+0.5)


plt.show()

# -- PLOT 4: average (# of transponders/(# of demands*# of nodes)) per island
plt.figure(num=4)
island_labels = ['Island ' + str(x) for x in range(1, 10)]
number_of_transponder_per_island_attribute_name = ['Transponder_Island' + str(x) for x in range(1, 10)]
x = np.arange(len(island_labels))
width = 0.35
# single transponder
# find the average number of transponders per island/number of demands
y_single = np.zeros(9)
for i in range(9):
    y_single[i] = results_single[number_of_transponder_per_island_attribute_name[i]].sum() / \
                  results_single['demands'].sum()
    y_single[i] = y_single[i]/island_size[i]
plt.bar(x-width/2, y_single, width, label="single")

# multiple transponder
# find the average number of transponders per island/number of demands
y_multiple = np.zeros(9)
for i in range(9):
    y_multiple[i] = results_multiple[number_of_transponder_per_island_attribute_name[i]].sum() / \
                    results_multiple['demands'].sum()
    y_multiple[i] = y_multiple[i]/island_size[i]

plt.bar(x+width/2, y_multiple, width, label="multiple")

plt.xticks(x, island_labels, rotation=90)
plt.title('Average number of transponders per demand and per node in each island')
plt.ylabel('# of transponders')
plt.grid(True)
plt.legend()
plt.show()

# -- PLOT 5: number of transponders per node
plt.figure(num=5)
num_demands = 700
# multiple transponder
results_multiple_filtered = results_multiple[results_multiple['demands'] == num_demands]
results_single_filtered = results_single[results_single['demands'] == num_demands]
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
    yZR_multiple[i] = results_multiple_filtered['ZR_Node' + node_labels[i]].sum()
    yLR_multiple[i] = results_multiple_filtered['LR_Node' + node_labels[i]].sum()
    yZR_single[i] = results_single_filtered['ZR_Node' + node_labels[i]].sum()
    yLR_single[i] = results_single_filtered['LR_Node' + node_labels[i]].sum()
    average_number_of_transponders_per_node_multiple[i] = (yZR_multiple[i]
                                                           + yLR_multiple[i])/results_multiple_filtered.shape[0]
    average_number_of_transponders_per_node_single[i] = (yZR_single[i]
                                                         + yLR_single[i])/results_single_filtered.shape[0]

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

# -- PLOT 6: top ten number of transponders per node in multiple case
plt.figure(num=6)
# find the top ten nodes with the highest number of transponders in the multiple transponder case
top_ten_nodes_multiple = np.argsort(average_number_of_transponders_per_node_multiple)[-10:]
top_ten_nodes_multiple = top_ten_nodes_multiple[::-1]
top_ten_nodes_multiple_labels = [node_labels[i] for i in top_ten_nodes_multiple]
top_ten_nodes_multiple_values = [average_number_of_transponders_per_node_multiple[i] for i in top_ten_nodes_multiple]

# plot a bar plot
x = np.arange(len(top_ten_nodes_multiple_labels))
width = 0.35
plt.bar(x, top_ten_nodes_multiple_values, width, label="multiple")
plt.xticks(x, top_ten_nodes_multiple_labels, rotation=90)
plt.title('Top ten nodes with the highest number of transponders in the multiple case')
plt.ylabel('# of transponders')
plt.grid(True)
plt.show()

# -- PLOT 7: top ten number of transponders per node in single transponder case
plt.figure(num=7)
# find the top ten nodes with the highest number of transponders in the single transponder case
top_ten_nodes = np.argsort(average_number_of_transponders_per_node_single)[-10:]
top_ten_nodes = top_ten_nodes[::-1]
top_ten_nodes_labels = [node_labels[i] for i in top_ten_nodes]
top_ten_nodes_values = [average_number_of_transponders_per_node_single[i] for i in top_ten_nodes]

# plot a bar plot
x = np.arange(len(top_ten_nodes_labels))
width = 0.35
plt.bar(x, top_ten_nodes_values, width, label="single")
plt.xticks(x, top_ten_nodes_labels, rotation=90)
plt.title('Top ten nodes with the highest number of transponders in the single case')
plt.ylabel('# of transponders')
plt.legend()
plt.grid(True)
plt.show()

# -- PLOT 8: top ten nodes with the highest number of transponders in the multiple case compared to the single case
plt.figure(num=8)

# extract number of transponders per node in the single case in the top_ten_nodes_multiple_labels
top_ten_nodes_single_values = [average_number_of_transponders_per_node_single[node_labels.index(x)] for x in top_ten_nodes_multiple_labels]

# plot a bar plot
x = np.arange(len(top_ten_nodes_multiple_labels))
width = 0.35
plt.bar(x-width/2, top_ten_nodes_multiple_values, width, label="multiple")
plt.bar(x+width/2, top_ten_nodes_single_values, width, label="single")
plt.xticks(x, top_ten_nodes_multiple_labels, rotation=90)
plt.title('Top ten nodes with the highest number of transponders in the multiple case compared to the single case')
plt.ylabel('# of transponders')
plt.legend()
plt.grid(True)
plt.show()


# -- PLOT 9: number of simulation failures in the multiple case
plt.figure(num=9)

# find the number of simulation failures for each number of demands computed as (number of iterations) - (number of rows having <number of demands> as demands attribute)

# compute the number of iterations as the number of rows having the minimum demands attribute
minimum_demands = results_multiple['demands'].min()
number_of_iterations = results_multiple[results_multiple['demands'] == minimum_demands].shape[0]

# compute the number of simulation failures for each number of demands
demands = results_multiple['demands'].unique()
number_of_simulation_failures = np.zeros(len(demands))
for i in range(len(demands)):
    number_of_simulation_failures[i] = number_of_iterations - results_multiple[results_multiple['demands'] == demands[i]].shape[0]

# plot a bar plot
x = np.arange(len(demands))
width = 0.35
plt.bar(x, number_of_simulation_failures, width, label="multiple")
plt.xticks(x, demands, rotation=90)
plt.title('Number of simulation failures over ' + str(number_of_iterations) + ' iterations')
plt.ylabel('# of failures')
plt.legend()
plt.grid(True)

plt.show()

# -- PLOT 10: number of simulation failures in the single case
plt.figure(num=10)

minimum_demands = results_single['demands'].min()
number_of_iterations = results_single[results_single['demands'] == minimum_demands].shape[0]
demands = results_single['demands'].unique()
number_of_simulation_failures = np.zeros(len(demands))
for i in range(len(demands)):
    number_of_simulation_failures[i] = number_of_iterations - results_single[results_single['demands'] == demands[i]].shape[0]

x = np.arange(len(demands))
width = 0.35
plt.bar(x, number_of_simulation_failures, width, label="single")
plt.xticks(x, demands, rotation=90)
plt.title('Number of simulation failures over ' + str(number_of_iterations) + ' iterations')
plt.ylabel('# of failures')
plt.legend()
plt.grid(True)
plt.show()










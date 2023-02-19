import os
import sys
import subprocess

from xml.dom import minidom


def readXML(xmlFileInput):
    resultFile = open('./' + xmlFileInput,'r')
    xmldoc = minidom.parse(resultFile)

    printNode(xmldoc.documentElement)

    resultFile.close()




def printNode(node):
  print(node)
  for child in node.childNodes:
       printNode(child)




output_folder = os.getcwd() + "/results"

if not os.path.exists(output_folder):
    print(output_folder)
    print("results folder missing")
    sys.exit(1)

os.walk(output_folder)


# os.walk() returns a tuple for each subdirectory, the first element is the directory name
subdirectory_list = [subdirectory_tuple[0] for subdirectory_tuple in os.walk(output_folder)]

print(subdirectory_list)

# gives a listo of immediate subdirectories of the sresult folder
immediate_subdirectory = next(os.walk('./results'))[1]

#span single vs multiple Transponder
mypath = './results/'

for subdir in immediate_subdirectory:
    mypath += subdir
    print(subdir)
    print(next(os.walk(mypath))[1])
    for case in next(os.walk(mypath))[1]:
        mypath = mypath + '/'+ case
        print("case:"+case)
        for demand in next(os.walk(mypath))[1]:
            print(demand)
            mypath = mypath + '/'+ demand + '/'
            filenames = next(os.walk(mypath), (None, None, []))[2]  # [] if no file
            
            print(filenames)

            # TODO: READ XML FILE
            if(filenames):
                for file in filenames:
                    readXML(mypath + file)


        mypath = './results/' + subdir
    mypath = './results/'
            


# PROBLEMA: legge un file solo dalla prima cartella, poi non vede gli altri
# PROBLEMA: LEGGERE L'XML COSÌ NON VA BENE PER AVERE I DATI











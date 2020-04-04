import xml.etree.ElementTree as ET
import numpy as np
from datetime import datetime
import json

def getDist(a, b):
    R = 6371000
    dx = abs(a[1]-b[1])/180*np.pi*R*np.cos((a[0]+b[0])/2*np.pi/180)
    dy = abs(a[0]-b[0])/180*np.pi*R
    return (dx**2 + dy**2)**0.5

def parseGPX(name):
    tree = ET.parse(name)
    root = tree.getroot()
    arr = []
    for entry in list(root[1][1]):
        if len(entry) < 2:
            t = -1
        else:
            t = datetime.fromisoformat(entry[1].text[:-1]).timestamp()
        val = [float(entry.attrib["lat"]), float(entry.attrib["lon"]), int(t)]
        arr.append(val)
    #fix missing times
    d = 0
    n = len(arr)
    pref = np.zeros(n)
    prefId = np.zeros(n, dtype=int)
    suf = np.zeros(n)
    sufId = np.zeros(n, dtype=int)
    curId = 0
    for i in range(len(arr)):
        if arr[i][2] == -1:
            d+= getDist(arr[i-1], arr[i])
        else:
            d = 0
            curId = i
        pref[i] = d
        prefId[i] = curId

    curId = n-1
    for i in range(len(arr)-1, -1, -1):
        if arr[i][2] == -1:
            d+= getDist(arr[i+1], arr[i])
        else:
            d = 0
            curId = i
        suf[i] = d
        sufId[i] = curId
    
    for i in range(len(arr)):
        if arr[i][2] == -1:
            arr[i][2] = int((suf[i]*arr[prefId[i]][2]+pref[i]*arr[sufId[i]][2])/(suf[i]+pref[i]))
    
    return arr

if __name__ == "__main__":
    # arr = parseGPX("gpx/GraphHopper(1).gpx")
    # import matplotlib.pyplot as plt
    # plt.plot(np.arange(len(arr)), [x[2] for x in arr])
    # plt.show()
    for i in range(1, 6):
        with open("../backend/data/" + str(i) + ".json", "w") as f:
            json.dump(parseGPX("gpx/GraphHopper(" + str(i) + ").gpx"), f)
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
            pass
        else:
            t = datetime.fromisoformat(entry[1].text[:-1]).timestamp()
        val = (entry.attrib["lat"], entry.attrib["lon"], int(t))
        arr.append(val)
    return arr

if __name__ == "__main__":
    for i in range(1, 6):
        with open("../backend/data/" + str(i) + ".json", "w") as f:
            json.dump(parseGPX("gpx/GraphHopper(" + str(i) + ").gpx"), f)
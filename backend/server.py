from flask import Flask
import json
import os

app = Flask(__name__)

sick_traces = []

def load_sick_traces():
    n = len(os.listdir("data"))
    for i in range(1, n+1):
        with open("data/" + str(i) + ".json") as f:
            sick_traces.append(json.load(f))

@app.route('/heartbeat/<offset>')
def heartbeat(offset):
    offset = int(offset)
    if offset >= 0 and offset < len(sick_traces):
        return json.dumps(sick_traces[offset:])
    return json.dumps([])


if __name__ == '__main__':
    load_sick_traces()
    app.run()
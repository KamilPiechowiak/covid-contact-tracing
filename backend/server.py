from flask import Flask
import json

app = Flask(__name__)

sick_traces = []

@app.route('/heartbeat/<offset>')
def heartbeat(offset):
    offset = int(offset)
    if offset >= 0 and offset < len(sick_traces):
        return json.dumps(sick_traces[offset:])
    return json.dumps([])


if __name__ == '__main__':
    app.run()
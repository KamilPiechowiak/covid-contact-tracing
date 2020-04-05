$(document).ready(function() {
  /// heatmap ///

  function randomDate(start, end) {
    var date = new Date(+start + Math.random() * (end - start));
    return moment(date).format('YYYY-MM-DD');
  }

  var events = (Math.random() * 200).toFixed(0);
  var data = [];
  for (var i = 0; i < events; i++) {
    data.push({
      count : parseInt((Math.random() * 200).toFixed(0)),
      date : randomDate(moment().subtract(16, 'days').format('x'),
                        moment().format('x'))
    });
  }

  $("#heatmap").CalendarHeatmap(data, {
    months : 5,
    coloring : "red",
    legend : {align : "left", minLabel : "Low", maxLabel : "High"},
    weekStartDay : 0,
    labels : {days : true, custom : {monthLabels : "MMM 'YY"}},
    tooltips : {show : true}
  });

  /// map ///

  var map = L.map('mapid').setView([ 52.4064, 16.9252 ], 14);

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
     attribution :
         '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
   }).addTo(map);

  /*var polygon =
L.polygon(
     [ [ 52.4064, 16.9252 ], [ 52.4194, 16.9352 ], [ 52.4224, 16.9252 ] ],
     {fillColor : '#f03', color : 'red'})
    .addTo(map);*/

  /////////////////////////////

	function dist(p1, p2) {
		return (1000*(p1[0] - p2[0])) ** 2 + (1000*(p1[1] - p2[1])) ** 2
	}

  function draw_route(data, color = "red") {
    var latlngs = [];

    var heatP = [];

	  var last_used = data[0];
	  for (i = 1; i < data.length; i++) {
		  //dist(data[i-1], data[i])
		  //console.log(dist(data[i-1], data[i]))
		  //latlngs.push([ data[i][0], data[i][1] ]);
		  if (dist(last_used, data[i]) > 0.01) {
			  heatP.push([ data[i][0], data[i][1], 0.5 ]);
			last_used = data[i];
		  }
    }

    var heat = L.heatLayer(heatP, {radius : 25}).addTo(map);

    return L.polyline(latlngs, {color : color}).addTo(map);
  }

  function draw_marks(data, color = "red") {
    for (i = 0; i < data.length; i++) {
      // latlngs.push([ data[i][0], data[i][1] ]);

      var dateString = moment.unix(data[i][2]).format("MM/DD/YYYY");

      console.log(data[i][0]);
      var popup = L.popup()
                      .setLatLng([ data[i][0], data[i][1] ])
                      .setContent(dateString)
                      .addTo(map);
      var marker = L.marker([ data[i][0], data[i][1] ]).addTo(map);
    }
  }

  // mark points from map

  function parse_query_string(query) {
    var vars = query.split("&");
    var query_string = {};
    for (var i = 0; i < vars.length; i++) {
      var pair = vars[i].split("=");
      var key = decodeURIComponent(pair[0]);
      var value = decodeURIComponent(pair[1]);
      if (typeof query_string[key] === "undefined") {
        query_string[key] = decodeURIComponent(value);
      } else if (typeof query_string[key] === "string") {
        var arr = [ query_string[key], decodeURIComponent(value) ];
        query_string[key] = arr;
      } else {
        query_string[key].push(decodeURIComponent(value));
      }
    }
    return query_string;
  }

  var query = window.location.search.substring(1);
  var qs = parse_query_string(query);

  console.log(qs);

  // atob()/btoa()

  points_base64 = btoa(
      "[ [ 52.4064, 16.9252 ], [ 52.4194, 16.9352 ], [ 52.4224, 16.9252 ] ]")
  console.log(points_base64);

  if (qs["mark"] != null) {
    // console.log("OKAY!", btoa("ok34433434"))
    points_base64 = qs["mark"]
    data = JSON.parse(atob(points_base64));
    console.log(data)
    draw_marks(data);
  }

  // load multiple files from directory
  // load files from file !!!!!!!!!

	var HOST = ""

	files = []
	for (var i = 1; i <= 15 ; i++) {
		files.push(HOST + 'data/' + i.toString() + '.json')
	}

	console.log(files)

		/*
  files = [
    'data/1.json', 'data/2.json', 'data/3.json', 'data/4.json', 'data/5.json',
    'data/6.json', 'data/7.json', 'data/8.json', 'data/9.json', 'data/10.json',
    'data/11.json', 'data/12.json', 'data/13.json', 'data/14.json',
    'data/15.json'
  ]*/

  paths = {};

  $.each(files, function(i, filename) {
    $.getJSON(filename, function(data) { paths[filename] = data; });
  })

  loaded = false;

  setInterval(function() {
    // console.log(paths);
    if (loaded == false && Object.keys(paths).length == files.length) {

      $.each(paths, function(i, d) {
        console.log(i);
        draw_route(d);
      });

      loaded = true;
    }
  }, 1000);

  /*$.getJSON('data/1.json', function(data) {
      console.log(data);
      draw_route(data)
    });*/
});

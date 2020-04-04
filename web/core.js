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

  var map = L.map('mapid').setView([ 52.4064, 16.9252 ], 13);

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
     attribution :
         '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
   }).addTo(map);

  var polygon =
      L.polygon(
           [ [ 52.4064, 16.9252 ], [ 52.4194, 16.9352 ], [ 52.4224, 16.9252 ] ],
           {fillColor : '#f03', color : 'red'})
          .addTo(map);
});

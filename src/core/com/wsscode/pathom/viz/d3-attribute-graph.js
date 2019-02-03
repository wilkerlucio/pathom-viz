import * as d3 from "d3";

const DEFAULTS = {

}

function init(element, settings) {
  const svg = d3.select(element)

  return Object.assign({}, DEFAULTS, settings, {svg, element})
}

export function render(element, data) {
  const settings = init(element, data)
  const {svg, svgWidth, svgHeight} = settings

  svg
    .attr('width', svgWidth)
    .attr('height', svgHeight)

  const {nodes, links} = settings.data

  // const {nodes, links} = {
  //   nodes: [
  //     {attribute: "foo"},
  //     {attribute: "bar"},
  //     {attribute: "foo2"},
  //     {attribute: "bar2"}
  //   ],
  //   links: [
  //     {source: "foo", target: "bar"},
  //     {source: "foo2", target: "bar2"},
  //     {source: "bar2", target: "foo2"}
  //   ]
  // }

  const simulation = d3.forceSimulation(nodes)
    .force("link", d3.forceLink(links).id(d => d.attribute).distance(60).strength(1))
    // .force("charge", d3.forceManyBody().strength(-1))
    .force("charge", d3.forceManyBody())
    .force("center", d3.forceCenter(svgWidth / 2, svgHeight / 2))

  const link = svg.append("g")
    .attr("stroke", "#999")
    .attr("stroke-opacity", 0.6)
    .selectAll("line")
    .data(links)
    .enter().append("line")
    .each(function (d) { d.ownerLine = d3.select(this)})
    .attr("stroke-width", d => 1);

  const drag = simulation => {
    function dragstarted(d) {
      if (!d3.event.active) simulation.alphaTarget(0.3).restart();
      d.fx = d.x;
      d.fy = d.y;
    }

    function dragged(d) {
      d.fx = d3.event.x;
      d.fy = d3.event.y;
    }

    function dragended(d) {
      if (!d3.event.active) simulation.alphaTarget(0);
      d.fx = null;
      d.fy = null;
    }

    return d3.drag()
      .on("start", dragstarted)
      .on("drag", dragged)
      .on("end", dragended);
  }

  let label

  const node = svg.append("g")
    .selectAll("circle")
    .data(nodes)
    .enter().append("circle")
    .attr('class', 'pathom-viz-index-explorer-attr-node')
    .attr("stroke-width", d => Math.sqrt(d.reach || 1) + 1)
    .attr("r", d => Math.sqrt(d.weight || 1) + 2)
    // .enter().append("text")
    // .attr("text-anchor", "middle")
    // .html(d => d.attribute)
    .each(function(d) {
      d.lineTargets = links.filter(l => {
        return l.source.attribute === d.attribute;
      });
      d.lineSources = links.filter(l => {
        return l.target.attribute === d.attribute;
      });
    })
    .on('click', function(d) {
      console.log(d, this)
    })
    .on('mouseenter', function(d) {
      d3.select(this).attr('fill', '#f00')

      d.lineTargets.forEach(line => {
        line.ownerLine
          .attr('stroke', '#0c0')
          .attr('stroke-width', 3)
      })

      d.lineSources.forEach(line => {
        line.ownerLine
          .attr('stroke', '#cc1a9d')
          .attr('stroke-width', 2)
      })

      return label.html(d.attribute)
    })
    .on('mouseleave', function(d) {
      d3.select(this).attr('fill', '#000')

      d.lineTargets.forEach(line => {
        line.ownerLine
          .attr('stroke', '#999')
          .attr('stroke-width', 1)
      })

      d.lineSources.forEach(line => {
        line.ownerLine
          .attr('stroke', '#999')
          .attr('stroke-width', 1)
      })
    })
    .call(drag(simulation))

  label = svg.append('text')
    .attr('x', 10)
    .attr('y', 30)
    .html('Hello')

  simulation.on("tick", () => {
    link
      .attr("x1", d => d.source.x)
      .attr("y1", d => d.source.y)
      .attr("x2", d => d.target.x)
      .attr("y2", d => d.target.y);

    node
      .attr("cx", d => d.x)
      .attr("cy", d => d.y);
  });

  return settings
}

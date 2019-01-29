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
    .force("charge", d3.forceManyBody().strength(-15))
    // .force("charge", d3.forceManyBody())
    .force("center", d3.forceCenter(svgWidth / 2, svgHeight / 2))

  const link = svg.append("g")
    .attr("stroke", "#999")
    .attr("stroke-opacity", 0.6)
    .selectAll("line")
    .data(links)
    .enter().append("line")
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

  const label = svg.append('text')
    .attr('x', 10)
    .attr('y', 30)
    .html('Hello')

  const node = svg.append("g")
    .attr("stroke", "#fff")
    .attr("stroke-width", 1.5)
    .selectAll("circle")
    .data(nodes)
    .enter().append("circle")
    .attr("r", d => Math.sqrt(d.weight || 1) + 2)
    .attr("fill", "#000")
    // .enter().append("text")
    // .attr("text-anchor", "middle")
    // .html(d => d.attribute)
    .on('click', d => console.log(d))
    .on('mouseenter', d => label.html(d.attribute))
    .call(drag(simulation))

  node.append("title")
    .text(d => d.id);

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

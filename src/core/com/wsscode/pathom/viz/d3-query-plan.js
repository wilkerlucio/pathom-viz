import d3 from "d3";

const DEFAULTS = {
  showDetails: function () {}
}

function init(element, settings) {
  const svg = d3.select(element)

  return Object.assign({}, DEFAULTS, settings, {svg, element})
}

export function render(element, data) {
  const settings = init(element, data)
  const {svg, svgWidth, svgHeight, showDetails, onClickEdge} = settings
  const colorScale = d3.scaleOrdinal(d3.schemeCategory10)

  svg
    .attr('width', svgWidth)
    .attr('height', svgHeight)

  const {nodes, links} = settings.data

  console.log("NODES", nodes, links);

  const container = svg.append("g")

  const zoom = d3.zoom().on("zoom",
    function () {
      container.attr("transform", d3.event.transform)
    }
  )

  svg.call(zoom);

  // build the arrows.
  svg.append("svg:defs").selectAll("marker")
    .data(["arrow-provides", "arrow-reaches"])
    .join("svg:marker")
    .attr("id", d => d)
    .attr("viewBox", "0 -5 10 10")
    .attr("refX", 15)
    .attr("refY", 0)
    .attr("markerWidth", 6)
    .attr("markerHeight", 6)
    .attr("markerUnits", 'userSpaceOnUse')
    .attr("orient", "auto")
    .attr('class', d => 'pathom-viz-planner-' + d)
    .append("svg:path")
    .attr("d", "M0,-5L10,0L0,5");

  const simulation = d3.forceSimulation(nodes)
    .force("link", d3.forceLink(links).id(d => d["node-id"]).distance(90).strength(0.1))
    .force("charge", d3.forceManyBody().strength(-60))
    .force('collision', d3.forceCollide().radius(d => {
      return d.radius * 1.6;
    }))
    .force("center", d3.forceCenter(svgWidth / 2, svgHeight / 2))

  settings.simulation = simulation

  const linksIndex = {};

  const highlightEdge = function (resolvers) {
    if (linksIndex[resolvers]) {
      linksIndex[resolvers].forEach(line => {
        line.classed("pathom-viz-planner-attr-link-focus-highlight", true)
      });

      label.html(resolvers)
    }
  }

  const unhighlightEdge = function (resolvers) {
    if (linksIndex[resolvers]) {
      linksIndex[resolvers].forEach(line => {
        line.classed("pathom-viz-planner-attr-link-focus-highlight", false)
      });

      label.html('')
    }
  }

  settings.highlightEdge = highlightEdge
  settings.unhighlightEdge = unhighlightEdge

  const link = container.append("g")
    .selectAll("line")
    .data(links)
    .join("svg:path")
    .attr('class', 'pathom-viz-planner-attr-link')
    .attr("marker-end", "url(#arrow-provides)")
    .classed('pathom-viz-planner-attr-link-branch', d => d["branch?"])
    .each(function (d) {
      d.ownerLine = d3.select(this)
      const lines = linksIndex[d.resolvers] || [];
      lines.push(d.ownerLine);
      linksIndex[d.resolvers] = lines;
    });

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

  const highlight = function (d) {
    d.nodeElement.classed('pathom-viz-planner-attr-node-highlight', true)

    d.lineTargets.forEach(line => {
      line.ownerLine.classed("pathom-viz-planner-attr-link-target-highlight", true)
    })

    d.lineSources.forEach(line => {
      line.ownerLine.classed("pathom-viz-planner-attr-link-source-highlight", true)
    })

    return label.html(d.attribute)
  }

  const unhighlight = function (d) {
    d.nodeElement.classed('pathom-viz-planner-attr-node-highlight', false)

    d.lineTargets.forEach(line => {
      line.ownerLine.classed("pathom-viz-planner-attr-link-target-highlight", false)
    })

    d.lineSources.forEach(line => {
      line.ownerLine.classed("pathom-viz-planner-attr-link-source-highlight", false)
    })

    return label.html('')
  }

  settings.highlightNode = function (id) {
    const d = nodes.find(x => x.attribute === id)

    if (d) highlight(d)
  }

  settings.unhighlightNode = function (id) {
    const d = nodes.find(x => x.attribute === id)

    if (d) unhighlight(d)
  }

  const node = container.append("g")
    .selectAll("circle")
    .data(nodes)
    .join("circle")
    .attr('class', 'pathom-viz-planner-attr-node')
    .classed('pathom-viz-planner-node-root', d => d["root?"])
    .classed('pathom-viz-planner-node-branch-and', d => d["run-and"])
    .classed('pathom-viz-planner-node-branch-or', d => d["run-or"])
    .attr("stroke-width", d => 1)
    .attr("r", d => d.radius)
    .each(function(d) {
      d.nodeElement = d3.select(this)

      d.lineTargets = links.filter(l => {
        return l.source.attribute === d.attribute;
      });
      d.lineSources = links.filter(l => {
        return l.target.attribute === d.attribute;
      });
    })
    .on('click', function(d) {
      console.log(d);
    })
    // .on('mouseenter', function(d) {
    //   return highlight(d)
    // })
    // .on('mouseleave', function(d) {
    //   return unhighlight(d)
    // })
    .call(drag(simulation))

  label = svg.append('text')
    .attr('x', 10)
    .attr('y', 30)
    .html('')

  simulation.on("tick", () => {
    link
      .attr("d", function (d) {
        const dx = d.target.x - d.source.x,
          dy = d.target.y - d.source.y,
          // dr = Math.sqrt(dx * dx + dy * dy),
          gamma = Math.atan2(dy,dx),
          tx = d.target.x - (Math.cos(gamma) * d.target.radius),
          ty = d.target.y - (Math.sin(gamma) * d.target.radius),

          sdx = d.source.x - d.target.x,
          sdy = d.source.y - d.target.y,
          sgamma = Math.atan2(sdy,sdx),
          sx = d.source.x - (Math.cos(sgamma) * d.source.radius),
          sy = d.source.y - (Math.sin(sgamma) * d.source.radius);
        return "M" +
          sx + "," +
          sy + "L" +
          tx + "," +
          ty;
      });

    node
      .attr("cx", d => d.x)
      .attr("cy", d => d.y);
  });

  settings.dispose = function () {
    simulation.stop()
  }

  return settings
}

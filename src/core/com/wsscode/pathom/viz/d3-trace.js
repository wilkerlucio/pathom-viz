import * as d3 from "d3";

d3.select('body').selectAll('.pathom-tooltip').remove()

const tooltipElement = d3.select('body').append("div").attr("class", 'pathom-tooltip')

function registerTooltip(nodes, labelF) {
  nodes
    .on('mouseover.tooltip', function(d) { tooltipElement.style('visibility', 'visible') })
    .on('mousemove.tooltip', function(d) {
      let x = d3.event.clientX + 15, y = d3.event.clientY;
      const x1 = x + tooltipElement.node().offsetWidth + 10;
      const screenWidth = document.body.offsetWidth;

      if (x1 > screenWidth) x = screenWidth - (x1 - x);

      tooltipElement
        .html(labelF(d, this))
        .style('left', x + 'px')
        .style('top', y + 'px')
    })
    .on('mouseout.tooltip', _ => tooltipElement.style('visibility', 'hidden'))
}

function renderRule(element, {vizHeight}) {
  const vruler = element
    .append('line')
    .attr('class', 'pathom-vruler')
    .attr('y1', 0)
    .attr('y2', vizHeight)

  element
    .on('mousemove.pathom-rule', function () {
      const x = d3.mouse(this)[0];
      vruler.attr('x1', x).attr('x2', x);
    })

  d3.select('body')
    .on('keydown.pathom-rule', e => {
      if (d3.event.keyCode === 16) vruler.style('visibility', 'visible')
    })
    .on('keyup.pathom-rule', e => {
      if (d3.event.keyCode === 16) vruler.style('visibility', 'hidden')
    })
}

function layoutTrace (node, {xScale, yScale, barSize}) {
  node.x0 = xScale(0)
  node.y0 = yScale(0);
  node.x1 = xScale(node.data.duration);
  node.y1 = node.y0 + 3;

  const positionNode = function (node, pos) {
    node.x0 = xScale(node.data.start);
    node.x1 = xScale(node.data.start + node.data.duration);
    node.y0 = pos.y;
    node.y1 = pos.y += barSize;
    pos.y++;

    const nextPos = {y: pos.y};

    if (node.data.children) {
      if (node.children) {
        node.children.forEach(n => positionNode(n, nextPos));
      }

      node.y2 = nextPos.y - 2;
    }

    pos.y = nextPos.y;

    return node;
  };

  const pos = {y: node.y1 + 1};
  node.children.forEach(n => positionNode(n, pos));

  return node;
}

function renderTrace(selection, settings) {
  const {data, transitionDuration, xScale} = settings

  const nodeRoots = selection
    .selectAll('g.pathom-attr-group')
    .data(layoutTrace(data, settings).descendants(), d => {
      return JSON.stringify(d.data.path)
    })

  const nodesEnter = nodeRoots
    .enter().append('g')

  const nodes = nodesEnter
    .attr('class', 'pathom-attr-group')
    .attr('transform', function (d) {
      return 'translate(' + [d.x0, d.y0] + ')'
    })
    .style('opacity', 0)
    .on('mouseover', function (d, i) {
      d3.select(this.childNodes[0]).style('visibility', 'visible');
    })
    .on('mouseout', function (d, i) {
      d3.select(this.childNodes[0]).style('visibility', 'hidden');
    })
    .on('click', d => {
      if (d.children && d.children.length && d.data.name) {
        d._children = d.children;
        d.children = null;
        renderTrace(selection, settings);
      } else if (d._children) {
        d.children = d._children;
        renderTrace(selection, settings);
      }
    })

  nodes
    .merge(nodeRoots)
    .transition().duration(transitionDuration)
    .attr('transform', function (d) {
      return 'translate(' + [d.x0, d.y0] + ')'
    })
    .style('opacity', 1)

  nodeRoots
    .exit()
    .transition().duration(transitionDuration)
    .style('opacity', 0)
    .remove()

  registerTooltip(nodes, (d, target) => {
    const label = d.data.name || d.data.hint;
    const xv = xScale.invert(d3.mouse(target)[0]);
    const details = d.data.details.filter(detail => {
      if (xv < detail.rts) return false;
      if (xv > (detail.rts + (detail.duration || xScale.invert(1)))) return false;

      return true;
    }).map(d => d.duration + ' ms ' + d.event);

    const childCount = d.data.children ? ' (' + d.data.children.length + ')' : '';

    return [d.data.duration + ' ms ' + label + childCount].concat(details).join("<br>");
  });

  const boundNodes = nodesEnter.append('rect')
    .attr('class', 'pathom-attribute-bounds')
    .attr('width', d => d.x1 - d.x0 + 1)
    .merge(nodeRoots.select('rect.pathom-attribute-bounds'))
    .transition().duration(transitionDuration)
    .attr('height', d => d.y2 ? d.y2 - d.y0 + 1 : 0)

  const attributeNodes = nodesEnter.append('rect')
    .attr('class', 'pathom-attribute')
    .merge(nodeRoots.select('rect.pathom-attribute'))
    .transition().duration(transitionDuration)
    .attr('width', d => d.x1 - d.x0)
    .attr('height', d => d.y1 - d.y0)

  const detailsNodesRoots = nodesEnter.append('g')
    .attr('class', 'pathom-details-container')
    .merge(nodeRoots.select('g.pathom-details-container'))
    .selectAll('rect.pathom-detail-marker')
    .data(d => {
      d.data.details.forEach((dt) => {
        dt.x0 = d.x0, dt.x1 = d.x1, dt.y0 = d.y0, dt.y1 = d.y1
        dt.rts = dt.start - d.data.start
      })
      return d.data.details;
    })

  const detailsNodesEnter = detailsNodesRoots.enter().append('rect')

  detailsNodesEnter
    .attr('class', d => 'pathom-detail-marker ' + 'pathom-event-' + d.event + (d.error ? ' pathom-event-error' : ''))
    .merge(detailsNodesRoots.select('rect.pathom-detail-marker'))
    .transition().duration(transitionDuration) // these down here are not updating...
    .attr('width', d => Math.max(1, xScale(d.duration)))
    .attr('height', function (d) {
      return d.y1 - d.y0;
    })
    .attr('transform', function (d) {
      return 'translate(' + [xScale(d.rts), 0] + ')'
    })

  // labels
  nodesEnter
    .append('text')
    .attr('class', 'pathom-label-text')
    .attr('dx', 2)
    .attr('dy', 13)
    .style('font-family', d => d.data.children ? "monospace" : "")
    .text(function (d) {
      if (d.data.name) return d.data.name;
    })
}

const traceDefaults = {
  barSize: 17
}

function initTrace(element, settings) {
  const svg = d3.select(element)

  return Object.assign({}, traceDefaults, settings, {svg, element})
}

function updateScale({axisNodes, axisX}) {
  axisNodes.call(axisX)

  axisNodes
    .select(".domain")
    .remove()

  axisNodes
    .selectAll("text")
    .attr("y", 0)
    .attr("x", -5)
    .style("text-anchor", "end")
}

export function renderPathomTrace(element, settingsSource) {
  const settings = initTrace(element, settingsSource)
  const {svg, svgWidth, svgHeight, data} = settings

  svg
    .attr('width', svgWidth)
    .attr('height', svgHeight)

  const xScale = d3.scaleLinear().domain([0, data.duration * 1.05]).range([0, svgWidth]);

  const yScale = function(y) {
    return y + 15;
  }

  settings.xScale = xScale
  settings.yScale = yScale
  settings.transitionDuration = 300

  function applyZoom() {
    const new_xScale = d3.event.transform.rescaleX(xScale);

    settings.axisX.scale(new_xScale)

    updateScale(settings)
    mainGroup.attr("transform", d3.event.transform);
  }

  const zoom = d3.zoom().on("zoom", applyZoom)

  svg.call(zoom);

  settings.axisX = d3.axisBottom(xScale)
    .tickFormat(d => d + " ms")
    .tickSize(svgHeight)
    .ticks(10);

  settings.axisNodes = svg.append("g")
    .attr("class", "pathom-axis")
    .attr("transform", "translate(0,0)")

  updateScale(settings)

  const mainGroup = svg.append('g')
  mainGroup.append('g').attr('class', 'pathom-view-nodes-container')

  const dataTree = d3.hierarchy(data, d => d.children)
  dataTree.sum(d => d.name ? 1 : 0);

  dataTree.each(d => {
    if (d.children && d.children.length > 20) {
      d._children = d.children;
      d.children = null;

      d._children.forEach(dd => {
        dd._children = dd.children;
        dd.children = null;
      })
    }
  })

  settings.data = dataTree

  renderTrace(svg.select('.pathom-view-nodes-container'), settings)
  renderRule(svg, settings)

  return settings;
}

export function updateTraceSize(settings) {
  const {svg, svgWidth, svgHeight} = settings

  settings.xScale.range([0, svgWidth])
  settings.axisX.tickSize(svgHeight)

  updateScale(settings)
  renderTrace(svg.select('.pathom-view-nodes-container'), settings)

  svg
    .attr('width', svgWidth)
    .attr('height', svgHeight)
}

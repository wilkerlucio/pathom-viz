import d3 from "d3";

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
    .on('mouseout.tooltip', () => tooltipElement.style('visibility', 'hidden'))
}

function renderRule(element, settings) {
  const {svgHeight} = settings
  const vruler = element
    .append('line')
    .attr('class', 'pathom-vruler')
    .attr('y1', 0)
    .attr('y2', svgHeight)

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

  settings.vruler = vruler
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
  if (node.children)
    node.children.forEach(n => positionNode(n, pos));

  return node;
}

function applyDataStyles(selection) {
  selection.each(function ({style}) {
    if (!style) return

    const sel = d3.select(this)

    for (let name in style) {
      sel.style(name, style[name])
    }
  })

  return selection
}

function eventsOnMouse({xScale}, d, target) {
  const xv = xScale.invert(d3.mouse(target)[0]);

  return d.data.details.filter(detail => {
    if (xv < detail.rts) return false;
    if (xv > (detail.rts + (detail.duration || xScale.invert(1)))) return false;

    return true;
  })
}

function renderTrace(selection, settings) {
  const {data, transitionDuration, xScale, showDetails, svgHeight} = settings

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
    .on('click', function (d) {
      const details = d3.event.altKey ? eventsOnMouse(settings, d, this) : d.data.details

      showDetails(details.map(x => {
        const d = Object.assign({}, x)

        delete d.x0
        delete d.x1
        delete d.y0
        delete d.y1

        return d
      }), d.data);
    })

  nodes
    .style('opacity', 0)
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
    const details = eventsOnMouse(settings, d, target)
    const detailsTimes = details.map(d => {
      return (d.duration ? d.duration.toFixed(3) + ' ms ' : '') + '<strong>' + d.event + '</strong>' + (d.label ? ' ' + d.label : '')
    })

    const detailsCount = ' <span class="pathom-details-count">' + d.data.details.length + '</span>'
    const childCount = d.data.children ? ' <span class="pathom-children-count">' + d.data.children.length + '</span>' : '';

    return [d.data.duration.toFixed(3) + ' ms <strong>' + label + '</strong>' + detailsCount + childCount].concat(detailsTimes).join("<br>");
  });

  const boundNodes = nodesEnter.append('rect')
    .attr('class', 'pathom-attribute-bounds')
    .attr('width', d => d.x1 - d.x0 + 1)
    .merge(nodeRoots.select('rect.pathom-attribute-bounds'))
    .transition().duration(transitionDuration)
    .attr('width', d => d.x1 - d.x0 + 1)
    .attr('height', d => d.y2 ? d.y2 - d.y0 + 1 : 0)

  const toggleChildrenNodes = nodesEnter.append('text')
    .attr('class', 'pathom-attribute-toggle-children')
    .style('visibility', d => d.children || d._children ? '' : 'hidden')
    .on('click', function (d, e) {
      d3.event.stopPropagation();

      if (d.children && d.children.length && d.data.name) {
        d._children = d.children;
        d.children = null;
        renderTrace(selection, settings);
      } else if (d._children) {
        d.children = d._children;
        renderTrace(selection, settings);
      }
    })
    .merge(nodeRoots.select('.pathom-attribute-toggle-children'))
    .text((d, i) => {
      if (i === 0) return ''

      return d.children ? '-' : '+'
    })

  const attributeNodes = nodesEnter.append('rect')
    .attr('class', 'pathom-attribute')
    .attr('width', d => d.x1 - d.x0)
    .attr('height', d => d.y1 - d.y0)
    .merge(nodeRoots.select('rect.pathom-attribute'))
    .transition().duration(transitionDuration)
    .attr('width', d => d.x1 - d.x0)

  const detailsNodesRoots = nodesEnter.append('g')
    .attr('class', 'pathom-details-container')
    .merge(nodeRoots.select('g.pathom-details-container'))
    .selectAll('rect.pathom-detail-marker')
    .data(d => {
      d.data.details.forEach((dt, i) => {
        dt.x0 = d.x0, dt.x1 = d.x1, dt.y0 = d.y0, dt.y1 = d.y1
        dt.rts = dt.start - d.data.start
        dt.id = i
      })
      return d.data.details
    }, dt => {
      return dt.id
    })

  const detailsNodesEnter = detailsNodesRoots.enter().append('rect')

  const allDetails = detailsNodesEnter
    .attr('class', d => 'pathom-detail-marker ' + 'pathom-event-' + d.event + (d.error ? ' pathom-event-error' : ''))
    .attr('width', d => Math.max(1, xScale(d.duration)))
    .attr('height', function (d) {
      return d.y1 - d.y0;
    })
    .attr('transform', function (d) {
      return 'translate(' + [xScale(d.rts), 0] + ')'
    })
    .call(applyDataStyles)
    .merge(detailsNodesRoots)

  allDetails
    .transition().duration(transitionDuration)
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
    .html(function (d) {
      if (d.data.name) return createNameHtml(d.data.name);
    })
}

function createNameHtml (name) {
  const matches = name.match(/^[^:]([^/]+\/)(.+)/)

  if (matches) {
    return "<tspan class='pathom-label-text-fade'>" + matches[1] + "</tspan>" + matches[2];
  } else {
    return name;
  }
}

const traceDefaults = {
  barSize: 17,
  showDetails: x => x
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

  settings.vruler.attr('y2', svgHeight)

  svg
    .attr('width', svgWidth)
    .attr('height', svgHeight)
}

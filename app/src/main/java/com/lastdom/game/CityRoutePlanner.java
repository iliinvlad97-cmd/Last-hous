package com.lastdom.game;

import java.util.*;

/**
 * Cosmetic A* graph: sampled streets and obstruction-safe short access/detour lanes. No game clock,
 * random numbers, residents, rewards or save fields are touched.
 */
final class CityRoutePlanner {
  static final float STEP = 3, MARGIN = 3;

  static final class Box {
    final float l, t, r, b;

    Box(float l, float t, float r, float b) {
      this.l = l;
      this.t = t;
      this.r = r;
      this.b = b;
    }

    boolean contains(float x, float y) {
      return x > l && x < r && y > t && y < b;
    }
  }

  final float width, height;
  final List<Box> obstacles = new ArrayList<>();
  final List<Box> entranceBuildings = new ArrayList<>();
  final MapLocation target;
  final Collection<MapLocation> locations;
  final boolean strictLabels;
  final List<Box> labels = new ArrayList<>();

  CityRoutePlanner(MapLocation target, Collection<MapLocation> locations, float height) {
    this(target, locations, height, true);
  }

  private CityRoutePlanner(
      MapLocation target, Collection<MapLocation> locations, float height, boolean strictLabels) {
    this.locations = locations;
    this.strictLabels = strictLabels;
    this.target = target;
    this.width = 388;
    this.height = height;
    for (float[] b : CityRoadGeometry.BLOCKS) {
      Box box =
          new Box(
              b[0] * width - MARGIN,
              b[1] * height - MARGIN,
              (b[0] + b[2]) * width + MARGIN + 4,
              (b[1] + b[3]) * height + MARGIN + 7);
      if (box.contains(target.mapX * width, target.mapY * height)) entranceBuildings.add(box);
      obstacles.add(box);
    }
    for (MapLocation m : locations) {
      if (m.id.equals(target.id)) continue;
      float x = m.mapX * width, y = m.mapY * height;
      int lines = m.markerName.split("\n").length;
      boolean district = m.kind == MapLocation.Kind.DISTRICT;
      float half = 14;
      for (String line : m.markerName.split("\n"))
        half = Math.max(half, line.length() * (district ? 3.6f : 3.2f) + MARGIN);
      float radius = district ? 25 : 24;
      obstacles.add(new Box(x - radius, y - radius, x + radius, y + radius));
      labels.add(
          new Box(
              x - half,
              y - (district ? 44 : 43) - (lines - 1) * (district ? 12 : 10),
              x + half,
              y - 26));
      labels.add(new Box(x - 42, y + 23, x + 42, y + (district ? 57 : 46)));
    }
    if (strictLabels) obstacles.addAll(labels);
  }

  private float[][] unavailable() {
    return strictLabels
        ? new CityRoutePlanner(target, locations, height, false).build()
        : new float[0][];
  }

  boolean clear(float x, float y) {
    for (Box b : obstacles) if (b.contains(x, y)) return false;
    return true;
  }

  boolean segment(float ax, float ay, float bx, float by) {
    return segment(ax, ay, bx, by, false);
  }

  private boolean segment(float ax, float ay, float bx, float by, boolean entrance) {
    double dx = bx - ax, dy = by - ay;
    for (Box b : obstacles) {
      if (entrance && entranceBuildings.contains(b)) continue;
      double low = 0, high = 1;
      if (Math.abs(dx) < .000001) {
        if (ax <= b.l || ax >= b.r) continue;
      } else {
        double a = (b.l + .0001 - ax) / dx, c = (b.r - .0001 - ax) / dx;
        low = Math.max(low, Math.min(a, c));
        high = Math.min(high, Math.max(a, c));
        if (low >= high) continue;
      }
      if (Math.abs(dy) < .000001) {
        if (ay <= b.t || ay >= b.b) continue;
      } else {
        double a = (b.t + .0001 - ay) / dy, c = (b.b - .0001 - ay) / dy;
        low = Math.max(low, Math.min(a, c));
        high = Math.min(high, Math.max(a, c));
      }
      if (low < high) return false;
    }
    return true;
  }

  double streetDistance(float x, float y) {
    double best = Double.MAX_VALUE;
    for (float[] road : CityRoadGeometry.ROADS)
      for (int i = 2; i < road.length; i += 2) {
        float ax = road[i - 2] * width,
            ay = road[i - 1] * height,
            bx = road[i] * width,
            by = road[i + 1] * height;
        double dx = bx - ax,
            dy = by - ay,
            t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
        best = Math.min(best, Math.hypot(x - ax - t * dx, y - ay - t * dy));
      }
    return best;
  }

  float[][] build() {
    int cols = (int) (width / STEP) + 1, rows = (int) (height / STEP) + 1, count = cols * rows;
    boolean[] safe = new boolean[count];
    double[] road = new double[count];
    for (int cell = 0; cell < count; cell++) {
      float x = cell % cols * STEP, y = cell / cols * STEP;
      safe[cell] = clear(x, y);
      road[cell] = streetDistance(x, y);
      if (!strictLabels) for (Box label : labels) if (label.contains(x, y)) road[cell] += 180;
    }
    // Heading is part of a graph node, so a turn penalty remains a valid edge cost.
    // The ninth heading is the virtual shelter attachment (no previous direction).
    int end = -1, states = count * 9;
    double best = Double.MAX_VALUE;
    double[] costs = new double[states];
    Arrays.fill(costs, Double.MAX_VALUE);
    int[] previous = new int[states];
    Arrays.fill(previous, -1);
    boolean[] done = new boolean[states];
    PriorityQueue<Node> queue = new PriorityQueue<>();
    for (int cell = 0; cell < count; cell++) {
      float x = cell % cols * STEP, y = cell / cols * STEP;
      double distance =
          Math.hypot(x - CityMapLayout.SHELTER_X * width, y - CityMapLayout.SHELTER_Y * height);
      if (safe[cell]
          && distance <= 130
          && segment(CityMapLayout.SHELTER_X * width, CityMapLayout.SHELTER_Y * height, x, y)) {
        int id = cell * 9 + 8;
        costs[id] = distance * 2 + road[cell];
        queue.add(
            new Node(
                id, costs[id] + Math.hypot(x - target.mapX * width, y - target.mapY * height)));
      }
    }
    boolean districtAccess =
        target.kind == MapLocation.Kind.DISTRICT
            && locations.stream().noneMatch(m -> m.id.equals(target.id))
            && !clear(target.mapX * width, target.mapY * height);
    int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1}, dy = {-1, -1, -1, 0, 0, 1, 1, 1};
    while (!queue.isEmpty()) {
      Node node = queue.remove();
      if (node.cost >= best) break;
      int id = node.id;
      if (done[id]) continue;
      done[id] = true;
      int cell = id / 9, x = cell % cols, y = cell / cols;
      float px = x * STEP, py = y * STEP;
      double distance = Math.hypot(px - target.mapX * width, py - target.mapY * height),
          terminal = costs[id] + distance * 2 + road[cell];
      if (distance <= 130
          && terminal < best
          && (districtAccess || segment(target.mapX * width, target.mapY * height, px, py, true))) {
        end = id;
        best = terminal;
      }
      for (int direction = 0; direction < 8; direction++) {
        int nx = x + dx[direction], ny = y + dy[direction];
        if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue;
        int nextCell = ny * cols + nx, next = nextCell * 9 + direction;
        if (!safe[nextCell] || done[next] || !segment(px, py, nx * STEP, ny * STEP)) continue;
        double length = Math.hypot(dx[direction], dy[direction]) * STEP,
            turn = id % 9 == 8 || id % 9 == direction ? 0 : 2;
        double value = costs[id] + length * (1 + Math.pow(road[nextCell] / 9, 2)) + turn;
        if (value < costs[next]) {
          costs[next] = value;
          previous[next] = id;
          queue.add(
              new Node(
                  next,
                  value
                      + Math.hypot(
                          nx * STEP - target.mapX * width, ny * STEP - target.mapY * height)));
        }
      }
    }
    if (end < 0) return unavailable();
    List<float[]> reverse = new ArrayList<>();
    for (int id = end; id >= 0; id = previous[id]) {
      reverse.add(new float[] {id / 9 % cols * STEP / width, id / 9 / cols * STEP / height});
    }
    Collections.reverse(reverse);
    reverse.add(0, new float[] {CityMapLayout.SHELTER_X, CityMapLayout.SHELTER_Y});
    reverse.add(
        districtAccess
            ? new float[] {end / 9 % cols * STEP / width, end / 9 / cols * STEP / height}
            : new float[] {target.mapX, target.mapY});
    // Coalesce collinear samples only; never shortcut a safe detour through an obstacle.
    List<float[]> points = new ArrayList<>();
    for (float[] point : reverse) {
      while (points.size() > 1) {
        float[] a = points.get(points.size() - 2), b = points.get(points.size() - 1);
        float cross = (b[0] - a[0]) * (point[1] - b[1]) - (b[1] - a[1]) * (point[0] - b[0]);
        if (Math.abs(cross) > 0.000001f) break;
        points.remove(points.size() - 1);
      }
      points.add(point);
    }
    return smooth(points);
  }

  private float[][] smooth(List<float[]> input) {
    List<float[]> output = new ArrayList<>();
    output.add(input.get(0));
    for (int i = 1; i < input.size() - 1; i++) {
      float[] a = input.get(i - 1), b = input.get(i), c = input.get(i + 1);
      float trim = Math.min(4, Math.min(length(a, b, height), length(b, c, height)) / 3);
      float u = trim / Math.max(.001f, length(a, b, height)),
          v = trim / Math.max(.001f, length(b, c, height));
      float[] from = {b[0] + (a[0] - b[0]) * u, b[1] + (a[1] - b[1]) * u},
          to = {b[0] + (c[0] - b[0]) * v, b[1] + (c[1] - b[1]) * v};
      List<float[]> arc = new ArrayList<>();
      arc.add(from);
      for (int k = 1; k <= 4; k++) {
        float t = k / 4f;
        arc.add(
            new float[] {
              (1 - t) * (1 - t) * from[0] + 2 * (1 - t) * t * b[0] + t * t * to[0],
              (1 - t) * (1 - t) * from[1] + 2 * (1 - t) * t * b[1] + t * t * to[1]
            });
      }
      boolean safe = true;
      for (int k = 1; k < arc.size(); k++)
        safe &=
            segment(
                arc.get(k - 1)[0] * width,
                arc.get(k - 1)[1] * height,
                arc.get(k)[0] * width,
                arc.get(k)[1] * height);
      if (safe) output.addAll(arc);
      else output.add(b);
    }
    output.add(input.get(input.size() - 1));
    return output.toArray(new float[0][]);
  }

  static final class Node implements Comparable<Node> {
    final int id;
    final double cost;

    Node(int id, double cost) {
      this.id = id;
      this.cost = cost;
    }

    public int compareTo(Node other) {
      int c = Double.compare(cost, other.cost);
      return c == 0 ? Integer.compare(id, other.id) : c;
    }
  }

  static float[] point(float[][] points, float progress, float height) {
    if (points.length == 0) return null;
    float total = 0;
    for (int i = 1; i < points.length; i++) total += length(points[i - 1], points[i], height);
    float left = Math.max(0, Math.min(1, progress)) * total;
    for (int i = 1; i < points.length; i++) {
      float length = length(points[i - 1], points[i], height);
      if (left <= length) {
        float t = length == 0 ? 1 : left / length;
        return new float[] {
          points[i - 1][0] + (points[i][0] - points[i - 1][0]) * t,
          points[i - 1][1] + (points[i][1] - points[i - 1][1]) * t
        };
      }
      left -= length;
    }
    return points[points.length - 1].clone();
  }

  static float length(float[] a, float[] b, float height) {
    return (float) Math.hypot((b[0] - a[0]) * 388, (b[1] - a[1]) * height);
  }
}

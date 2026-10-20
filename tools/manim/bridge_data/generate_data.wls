(* Flat Wolfram -> JSON bridge demo.

   Contract: a JSON list of records. Every record has an id and kind;
   sampled_polyline / parametric carry points, scalar_series carries values,
   bars carries values/categories, table carries header/rows, surface carries
   a shape and grid points, and points3d carries 3D points. Keep this file free
   of presentation logic: colors and widths are only defaults, and the Manim
   scene can override them. *)

x = Range[-5., 5., 0.02];

sinCurve = Table[{t, Sin[t] + t/5}, {t, x}];
shifted = Table[{t, Sin[t + 0.8] + t/5}, {t, x}];
slope = Table[N[Cos[t] + 1/5], {t, x}];

(* height field z = sin(x) cos(y), row-major (x outer, y inner) *)
surfaceSize = 21;
surfaceAxis = Subdivide[-2., 2., surfaceSize - 1];
surfacePoints = Flatten[
  Table[{x, y, Sin[x] Cos[y]}, {x, surfaceAxis}, {y, surfaceAxis}],
  1
];

spaceCurve = Table[
  {t, Cos[t], Sin[t], t/(2 Pi)},
  {t, Subdivide[0., 2 Pi, 120]}
];
scatter = Table[
  {Cos[t], Sin[t], t/(2 Pi)},
  {t, Subdivide[0., 2 Pi, 24]}
];

records = {
  <|
    "id" -> "sin_curve",
    "kind" -> "sampled_polyline",
    "style" -> <|"color" -> "#FF6A00", "stroke_width" -> 5, "smooth" -> True|>,
    "points" -> sinCurve
  |>,
  <|
    "id" -> "phase_shifted",
    "kind" -> "sampled_polyline",
    "style" -> <|"color" -> "#00A8FF", "stroke_width" -> 5, "smooth" -> True|>,
    "points" -> shifted
  |>,
  <|
    "id" -> "slope",
    "kind" -> "scalar_series",
    "style" -> <|"color" -> "#00C1E4"|>,
    "values" -> slope
  |>,
  <|
    "id" -> "bars",
    "kind" -> "bars",
    "categories" -> {"E3", "PGA", "CGA"},
    "values" -> {3., 5., 4.},
    "style" -> <|
      "color" -> "#00A8FF",
      "secondary_color" -> "#FF6A00",
      "max_value" -> 5.
    |>
  |>,
  <|
    "id" -> "table",
    "kind" -> "table",
    "header" -> {"operation", "result"},
    "rows" -> {
      {"e1 e2", "e3"},
      {"R v R~", "rotor"},
      {"z = sin(x) cos(y)", "surface"}
    },
    "style" -> <|
      "color" -> "#D3DAE3",
      "header_color" -> "#66C2F2",
      "grid_color" -> "#666A73",
      "font_size" -> 20.
    |>
  |>,
  <|
    "id" -> "surface",
    "kind" -> "surface",
    "shape" -> {surfaceSize, surfaceSize},
    "points" -> surfacePoints,
    "style" -> <|"color" -> "#B30DBF", "opacity" -> 0.55|>
  |>,
  <|
    "id" -> "space_curve",
    "kind" -> "parametric",
    "style" -> <|"color" -> "#DFFC00", "stroke_width" -> 5|>,
    "points" -> spaceCurve
  |>,
  <|
    "id" -> "scatter",
    "kind" -> "points3d",
    "style" -> <|"color" -> "#00C1E4", "radius" -> 0.06|>,
    "points" -> scatter
  |>
};

(* Wolfram's path handling mangles this machine's CJK project path. Keep the
   output relative and run this script from tools/manim. *)
outDir = "bridge_data/data";
If[! DirectoryQ[outDir], CreateDirectory[outDir]];
outFile = FileNameJoin[{outDir, "demo.json"}];
Export[outFile, Normal[records], "JSON"];

Print["wrote ", outFile];
Print["records: ", Length[records], "  points: ", Length[sinCurve]];

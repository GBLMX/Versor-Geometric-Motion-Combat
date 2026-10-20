(* Flat Wolfram -> JSON bridge demo.

   Contract: a JSON list of records. Every record has an id and kind;
   sampled_polyline / parametric carry points, scalar_series carries values.
   Keep this file free of presentation logic: colors and widths are only
   defaults, and the Manim scene can override them. *)

x = Range[-5., 5., 0.02];

sinCurve = Table[{t, Sin[t] + t/5}, {t, x}];
shifted = Table[{t, Sin[t + 0.8] + t/5}, {t, x}];
slope = Table[N[Cos[t] + 1/5], {t, x}];

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

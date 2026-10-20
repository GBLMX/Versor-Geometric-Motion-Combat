(* Riemann zeta on the critical line, exported as a flat bridge record.

   points are [t, Re(zeta(1/2 + i t)), Im(zeta(1/2 + i t))].
   Run from tools/manim because the output path is relative. *)

t = Range[0., 50., 0.05];
points = Table[
  {tt, Re[Zeta[0.5 + I tt]], Im[Zeta[0.5 + I tt]]},
  {tt, t}
];

records = {
  <|
    "id" -> "zeta_critical",
    "kind" -> "parametric",
    "style" -> <|"color" -> "#E24BE8", "stroke_width" -> 5|>,
    "points" -> points
  |>
};

outDir = "bridge_data/data";
If[! DirectoryQ[outDir], CreateDirectory[outDir]];
outFile = FileNameJoin[{outDir, "zeta.json"}];
Export[outFile, Normal[records], "JSON"];

Print["wrote ", outFile];
Print["samples: ", Length[points], "  t: ", First[t], "..", Last[t]];
Print["Re range: ", Min[points[[All, 2]]], "..", Max[points[[All, 2]]]];
Print["Im range: ", Min[points[[All, 3]]], "..", Max[points[[All, 3]]]];

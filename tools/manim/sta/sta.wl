(* ::Package:: *)
(* STA (Cl(1,3)) quantities for the manim scene, computed with the
   built-in CliffordAlgebra (WL 15.0):
     gamma_0^2 = +1, gamma_i^2 = -1 (i = 1,2,3), gamma_mu gamma_nu
     anticommute for mu != nu. *)

sta = CliffordAlgebra[{{e0}, {e1, e2, e3}}];
gens = {e0, e1, e2, e3};

blades = {1} ~Join~ gens ~Join~
  {e0 ** e1, e0 ** e2, e0 ** e3, e1 ** e2, e1 ** e3, e2 ** e3} ~Join~
  {e0 ** e1 ** e2, e0 ** e1 ** e3, e0 ** e2 ** e3, e1 ** e2 ** e3} ~Join~
  {e0 ** e1 ** e2 ** e3};

grade[b_] := If[b === 1, 0, Count[b, Alternatives @@ gens, {0, Infinity}]];
revB[b_] := (-1)^(grade[b] (grade[b] - 1)/2) b;
reverse[x_] := Expand[NonCommutativeExpand[x, sta] /. Thread[blades -> revB /@ blades]];

(* 16 coefficients, scalar blade handled explicitly *)
bSyms = Table[Unique["b"], 16];
bladeRule = Thread[blades -> bSyms];
bSet = Alternatives @@ bSyms;
poly[x_] := Module[{e},
  e = Expand[NonCommutativeExpand[x, sta] /. bladeRule];
  e = If[Head[e] === Plus,
    Replace[e, t_ /; FreeQ[t, bSet] :> t bSyms[[1]], {1}],
    If[FreeQ[e, bSet], e bSyms[[1]], e]];
  Expand[e]];
comp[x_, i_] := Coefficient[poly[x], bSyms[[i]]];

sandwich[R_, x_] := NonCommutativeExpand[R ** x ** reverse[R], sta];

(* --- signature and rotor unitarity ---------------------------------- *)
sig = Table[FullSimplify[comp[NonCommutativeExpand[gens[[i]] ** gens[[j]], sta], 1]],
  {i, 4}, {j, 4}];

boostR[p_] := Cosh[p/2] - Sinh[p/2] (e0 ** e1);
boostY[p_] := Cosh[p/2] - Sinh[p/2] (e0 ** e2);
rotorCheck = FullSimplify[NonCommutativeExpand[boostR[phi] ** reverse[boostR[phi]], sta]];

phi = N@Subdivide[0, 3, 60];

(* components along (gamma_0, gamma_1, gamma_2, gamma_3) *)
vecData[mu_] := Table[
  With[{e = sandwich[boostR[p], gens[[mu]]]},
    Table[N[comp[e, k]], {k, 2, 5}]],
  {p, phi}];

(* --- Thomas / Wigner rotation: boost_y(phi) after boost_x(phi) ------ *)
thomasRotor[p_] := NonCommutativeExpand[boostY[p] ** boostR[p], sta];

(* spatial part of the Lorentz map; columns are the images of
   gamma_1, gamma_2, gamma_3 *)
spatialMatrix[p_] := Table[
  With[{v = sandwich[thomasRotor[p], gens[[j + 1]]]},
    Table[N[comp[v, k]], {k, 3, 5}]],
  {j, 3}];

polarRotation[A_] := Module[{u, s, v, r},
  {u, s, v} = SingularValueDecomposition[A];
  r = u . Transpose[v];
  If[Det[r] < 0, r = u . DiagonalMatrix[{1, 1, -1}] . Transpose[v]];
  r];

thomasData := Table[
  With[{A = spatialMatrix[p]},
    Module[{r = polarRotation[A]},
      <|"A" -> A, "R" -> r,
        "angle" -> ArcCos[Clip[(Tr[r] - 1)/2, {-1, 1}]]|>]],
  {p, phi}];

th = thomasData;

(* polar decomposition checks on the last sample *)
rLast = th[[-1, "R"]];
aLast = th[[-1, "A"]];
stretch = Transpose[rLast] . aLast;
checkOrth = Max[Abs[rLast . Transpose[rLast] - IdentityMatrix[3]]];
checkSym = Max[Abs[stretch - Transpose[stretch]]];
checkEig = Min[Eigenvalues[(stretch + Transpose[stretch])/2]];
checkZero = Max[Abs[th[[1, "angle"]]]];

(* --- export ---------------------------------------------------------- *)
meta = <|
  "checks" -> <|
    "signatureMatrix" -> sig,
    "rotorNorm" -> rotorCheck,
    "wignerOrthogonalityError" -> checkOrth,
    "wignerDet" -> Det[rLast],
    "wignerStretchSymmetryError" -> checkSym,
    "wignerStretchMinEigenvalue" -> checkEig,
    "wignerAngleAtZero" -> checkZero
  |>
|>;

data = <|
  "meta" -> meta,
  "boost" -> <|
    "phi" -> phi,
    "vectors" -> <|"e0" -> vecData[1], "e1" -> vecData[2],
      "e2" -> vecData[3], "e3" -> vecData[4]|>,
    "contraction" -> Table[N[1/Cosh[p]], {p, phi}]
  |>,
  "thomas" -> <|
    "phi" -> phi,
    "angle" -> th[[All, "angle"]],
    "A" -> th[[All, "A"]],
    "R" -> th[[All, "R"]]
  |>
|>;

outDir = FileNameJoin[{DirectoryName[$InputFileName], "data"}];
If[! DirectoryQ[outDir], CreateDirectory[outDir]];
Export[FileNameJoin[{outDir, "sta.json"}], data, "JSON"];

Print["signature: ", MatrixForm[sig]];
Print["R Rev(R): ", rotorCheck];
Print["polar checks: orth=", checkOrth, " sym=", checkSym, " minEig=", checkEig];
Print["Wigner angle (deg) at phi=0,0.5,1,2,3: ",
  Table[th[[First@First@Position[phi, x_ /; Abs[x - p] < 10.^-6], "angle"]]/Degree,
    {p, {0, 0.5, 1, 2, 3}}]];
Print["wrote data/sta.json"];

package com.example.data

object TestScheduleSeedData {
    val rawScheduleText = """
19-Jul-2026|Milestone|4|Main|Moving Charges and Magnetism;Magnetism and Matter|Haloalkanes and Haloarenes;Alcohols, Phenols and Ethers|Continuity and Differentiability;Method of Differentiation
2-Aug-2026|Milestone|5|Main|Electric Charges and Fields;Electrostatic Potential and Capacitance;Current Electricity;Moving Charges and Magnetism;Magnetism and Matter|Solutions;Electrochemistry;Chemical Kinetics;Organic 11th - Revision (GOC & Hydrocarbon);Haloalkanes and Haloarenes;Alcohols, Phenols and Ethers|Determinants;Matrices;Relations and Functions;Inverse Trigonometric Functions;Continuity and Differentiability;Method of Differentiation
9-Aug-2026|AITS|1|Main|Electric Charges and Fields;Electrostatic Potential and Capacitance|Solutions;Electrochemistry|Determinants;Matrices;Relations and Functions
16-Aug-2026|Milestone|6|Advanced|Electric Charges and Fields;Electrostatic Potential and Capacitance;Current Electricity;Moving Charges and Magnetism;Magnetism and Matter|Solutions;Electrochemistry;Chemical Kinetics;The Solid State;Surface Chemistry;Organic 11th - Revision (GOC & Hydrocarbon);Haloalkanes and Haloarenes;Alcohols, Phenols and Ethers|Determinants;Matrices;Relations and Functions;Inverse Trigonometric Functions;Continuity and Differentiability;Method of Differentiation
6-Sep-2026|Milestone|7|Main|Electromagnetic Induction;Alternating Current;Electromagnetic Waves|Aldehydes, Ketones and Carboxylic Acids;Amines;Biomolecules|Application of Derivatives;Indefinite Integration;Definite Integration
20-Sep-2026|AITS|2|Main|Current Electricity;Moving Charges and Magnetism;Magnetism and Matter + 30% cumulative|Chemical Kinetics;Haloalkanes and Haloarenes;Alcohols, Phenols and Ethers + 30% cumulative|Inverse Trigonometric Functions;Continuity and Differentiability;Method of Differentiation + 30% cumulative
27-Sep-2026|AITS|3|Advanced|Electric Charges and Fields;Electrostatic Potential and Capacitance;Current Electricity;Moving Charges and Magnetism;Magnetism and Matter|Solutions;Electrochemistry;Chemical Kinetics;Haloalkanes and Haloarenes;Alcohols, Phenols and Ethers|Determinants;Matrices;Relations and Functions;Inverse Trigonometric Functions;Continuity and Differentiability;Method of Differentiation
4-Oct-2026|Milestone|8|Main|Ray Optics and Optical Instruments|Coordination Compounds|Application of Integrals;Differential Equation
11-Oct-2026|Milestone|9|Main|Electric Charges and Fields;Electrostatic Potential and Capacitance;Current Electricity;Moving Charges and Magnetism;Magnetism and Matter;Electromagnetic Induction;Alternating Current;Electromagnetic Waves|Solutions;Electrochemistry;Organic 11th - Revision (GOC & Hydrocarbon);Haloalkanes and Haloarenes;Alcohols, Phenols and Ethers;Aldehydes, Ketones and Carboxylic Acids;Amines;Biomolecules;Coordination Compounds|Determinants;Matrices;Relations and Functions;Inverse Trigonometric Functions;Continuity and Differentiability;Method of Differentiation;Application of Derivatives;Indefinite Integration;Definite Integration
25-Oct-2026|Milestone|10|Main|Wave Optics;Dual Nature of Radiation and Matter;Atoms;Nuclei;Semiconductor Electronics - Materials, Devices and Simple Circuits|The p-Block Elements;The d and f-Block Elements;Salt analysis|Vector Algebra;Three-Dimensional Geometry;Linear Programming;Probability
1-Nov-2026|AITS|4|Main|Electromagnetic Induction;Alternating Current;Electromagnetic Waves + 30% cumulative|Aldehydes, Ketones and Carboxylic Acids;Amines;Biomolecules;Coordination Compounds + 30% cumulative|Application of Integrals;Indefinite Integration;Definite Integration + 30% cumulative
22-Nov-2026|Milestone|11|Advanced|Full Syllabus 12th|Full Syllabus 12th|Full Syllabus 12th
6-Dec-2026|Milestone|12|Main|Full Syllabus 11th|Full Syllabus 11th|Full Syllabus 11th
13-Dec-2026|AITS|5|Main|Ray Optics and Optical Instruments;Wave Optics;Dual Nature of Radiation and Matter;Atoms + 30% cumulative|The p-Block Elements;The d- and f-block Elements;Salt analysis + 30% cumulative|Application of Integrals;Differential Equations;Vector Algebra + 30% cumulative
20-Dec-2026|AITS|6|Advanced|Electromagnetic Induction;Alternating Current;Electromagnetic Wave;Ray Optics and Optical Instruments;Wave Optics;Dual Nature of Radiation and Matter;Atoms + 30% cumulative|Amines;Biomolecules;Coordination Compounds;The p-Block Elements;The d- and f-block Elements;Salt analysis + 30% cumulative|Application of Derivatives;Indefinite Integration;Definite Integration;Application of Integrals;Differential Equations;Vector Algebra + 30% cumulative
3-Jan-2027|AITS|7|Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main
10-Jan-2027|AITS|8|Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main
13-Jan-2027|AITS|9|Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main
17-Jan-2027|AITS|10|Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main
20-Jan-2027|AITS|11|Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main
7-Feb-2027|AITS|12|Main|Full Syllabus|Full Syllabus|Full Syllabus
21-Mar-2027|AITS|13|Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main
28-Mar-2027|AITS|14|Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main
31-Mar-2027|AITS|15|Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main|Full Syllabus * As per NTA JEE Main
18-Apr-2027|AITS|16|Advanced|Full Syllabus|Full Syllabus|Full Syllabus
25-Apr-2027|AITS|17|Advanced|Full Syllabus|Full Syllabus|Full Syllabus
2-May-2027|AITS|18|Advanced|Full Syllabus|Full Syllabus|Full Syllabus
9-May-2027|AITS|19|Advanced|Full Syllabus|Full Syllabus|Full Syllabus
12-May-2027|AITS|20|Advanced|Full Syllabus|Full Syllabus|Full Syllabus
""".trim()
}

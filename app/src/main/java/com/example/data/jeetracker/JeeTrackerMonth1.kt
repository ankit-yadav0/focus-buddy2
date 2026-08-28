// AUTO-GENERATED from jee-120-day-master-tracker source data (month 1).
// Do not hand-edit; regenerate from the original month1.ts file if plan content changes.
package com.example.data.jeetracker

internal object JeeTrackerMonth1 {
    fun days(): List<JeeDayPlan> = listOf(
        day1(),
        day2(),
        day3(),
        day4(),
        day5(),
        day6(),
        day7(),
        day8(),
        day9(),
        day10(),
        day11(),
        day12(),
        day13(),
        day14(),
        day15(),
        day16(),
        day17(),
        day18(),
        day19(),
        day20(),
        day21(),
        day22(),
        day23(),
        day24(),
        day25(),
        day26(),
        day27(),
        day28(),
        day29(),
        day30(),
    )

    private fun day1(): JeeDayPlan = JeeDayPlan(
        dayNumber = 1,
        month = 1,
        week = 1,
        dayOfWeek = "Mon",
        theme = "Basic Maths (Phy) & Determinants intro",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d1-t1",
                title = "Basic Maths",
                subject = "Physics",
                grade = "11th",
                phase = "Phase 1",
                targetDetails = "Target: 2h video + 1h practice",
                totalDuration = "6h 18m total chapter",
                lectures = "Part 1",
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d1-t2",
                title = "Determinants",
                subject = "Mathematics",
                grade = "12th",
                phase = "Phase 2",
                targetDetails = "Lectures 1–3 + DPP Practice",
                totalDuration = null,
                lectures = "Lec 1–3",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day2(): JeeDayPlan = JeeDayPlan(
        dayNumber = 2,
        month = 1,
        week = 1,
        dayOfWeek = "Tue",
        theme = "Basic Maths (Phy) & Determinants (Lec 4-6)",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d2-t1",
                title = "Basic Maths",
                subject = "Physics",
                grade = "11th",
                phase = "Phase 1",
                targetDetails = "Target: Next 2h video + Problem solving",
                totalDuration = "6h 18m total",
                lectures = "Part 2",
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d2-t2",
                title = "Determinants",
                subject = "Mathematics",
                grade = "12th",
                phase = "Phase 2",
                targetDetails = "Lectures 4–6 + Properties of Determinants",
                totalDuration = null,
                lectures = "Lec 4–6",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day3(): JeeDayPlan = JeeDayPlan(
        dayNumber = 3,
        month = 1,
        week = 1,
        dayOfWeek = "Wed",
        theme = "Mole Concept & Solutions intro",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d3-t1",
                title = "Mole Concept",
                subject = "Chemistry",
                grade = "11th",
                phase = "Phase 1",
                targetDetails = "Target: 3h video + Mole Calculations",
                totalDuration = "10h 37m total chapter",
                lectures = "Part 1",
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d3-t2",
                title = "Solution",
                subject = "Chemistry",
                grade = "12th",
                phase = "Phase 2",
                targetDetails = "Lectures 1–3: Concentration terms & Henry Law",
                totalDuration = null,
                lectures = "Lec 1–3",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day4(): JeeDayPlan = JeeDayPlan(
        dayNumber = 4,
        month = 1,
        week = 1,
        dayOfWeek = "Thu",
        theme = "Electrostatics & Basic Maths (Maths 11)",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d4-t1",
                title = "Electrostatics",
                subject = "Physics",
                grade = "12th",
                phase = "Phase 1",
                targetDetails = "Lectures 1–3: Coulomb Law & Electric Field",
                totalDuration = null,
                lectures = "Lec 1–3",
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d4-t2",
                title = "Basic Maths",
                subject = "Mathematics",
                grade = "11th",
                phase = "Phase 2",
                targetDetails = "Target: 3h video / Logarithms & Inequalities",
                totalDuration = "8h 35m total",
                lectures = "Part 1",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day5(): JeeDayPlan = JeeDayPlan(
        dayNumber = 5,
        month = 1,
        week = 1,
        dayOfWeek = "Fri",
        theme = "Electrostatics & Mole Concept continuation",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d5-t1",
                title = "Electrostatics",
                subject = "Physics",
                grade = "12th",
                phase = "Phase 1",
                targetDetails = "Lectures 4–6: Electric Field Lines & Flux",
                totalDuration = null,
                lectures = "Lec 4–6",
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d5-t2",
                title = "Mole Concept",
                subject = "Chemistry",
                grade = "11th",
                phase = "Phase 2",
                targetDetails = "Target: Next 3h video + Limiting Reagent",
                totalDuration = "10h 37m total",
                lectures = "Part 2",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day6(): JeeDayPlan = JeeDayPlan(
        dayNumber = 6,
        month = 1,
        week = 1,
        dayOfWeek = "Sat",
        theme = "Determinants (Lec 7-11) & Solution (Lec 4-7)",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d6-t1",
                title = "Determinants",
                subject = "Mathematics",
                grade = "12th",
                phase = "Phase 1",
                targetDetails = "Lectures 7–11: System of linear equations & Cramer Rule",
                totalDuration = null,
                lectures = "Lec 7–11",
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d6-t2",
                title = "Solution",
                subject = "Chemistry",
                grade = "12th",
                phase = "Phase 2",
                targetDetails = "Lectures 4–7: Raoult Law & Colligative Properties",
                totalDuration = null,
                lectures = "Lec 4–7",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day7(): JeeDayPlan = JeeDayPlan(
        dayNumber = 7,
        month = 1,
        week = 1,
        dayOfWeek = "Sun",
        theme = "🎯 Week 1 Mock Test & In-depth Error Analysis",
        isMockDay = true,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d7-t1",
                title = "3-Hour JEE Main Mock Test #1",
                subject = "Mock",
                grade = "Special",
                phase = "Mock Test",
                targetDetails = "10:00 AM – 1:00 PM (Simulated Exam Environment)",
                totalDuration = "3 Hours",
                lectures = null,
                isKeyMilestone = true
            ),
            JeeTask(
                id = "d7-t2",
                title = "Post-Exam Error Analysis & Weak Topic Logging",
                subject = "Revision",
                grade = "Special",
                phase = "Phase 2",
                targetDetails = "Log silly mistakes, formula gaps, and calculate net score",
                totalDuration = "2 Hours",
                lectures = null,
                isKeyMilestone = false
            ),
        ),
        mockDetails = JeeMockDetails(
            title = "JEE Main Full Mock Test 01",
            timing = "10:00 AM – 1:00 PM",
            description = "Test syllabus covered in Week 1 + analysis of speed & accuracy."
        ),
    )

    private fun day8(): JeeDayPlan = JeeDayPlan(
        dayNumber = 8,
        month = 1,
        week = 2,
        dayOfWeek = "Mon",
        theme = "Units & Dimensions & Matrices intro",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d8-t1",
                title = "Basic Maths (Finish) + Unit & Dimensions",
                subject = "Physics",
                grade = "11th",
                phase = "Phase 1",
                targetDetails = "Remaining 1h 18m Basic Maths + First 2h Units & Dimensions",
                totalDuration = "6h 07m total for U&D",
                lectures = null,
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d8-t2",
                title = "Matrices",
                subject = "Mathematics",
                grade = "12th",
                phase = "Phase 2",
                targetDetails = "Lectures 1–4: Types of Matrices & Matrix Multiplication",
                totalDuration = null,
                lectures = "Lec 1–4",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day9(): JeeDayPlan = JeeDayPlan(
        dayNumber = 9,
        month = 1,
        week = 2,
        dayOfWeek = "Tue",
        theme = "Unit & Dimensions & Matrices continuation",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d9-t1",
                title = "Unit & Dimensions",
                subject = "Physics",
                grade = "11th",
                phase = "Phase 1",
                targetDetails = "Next 3h video + Dimensional analysis & Error analysis",
                totalDuration = "6h 07m total",
                lectures = null,
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d9-t2",
                title = "Matrices",
                subject = "Mathematics",
                grade = "12th",
                phase = "Phase 2",
                targetDetails = "Lectures 5–8: Transpose, Symmetric & Adjoint",
                totalDuration = null,
                lectures = "Lec 5–8",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day10(): JeeDayPlan = JeeDayPlan(
        dayNumber = 10,
        month = 1,
        week = 2,
        dayOfWeek = "Wed",
        theme = "Mole Concept finish & Solutions advance",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d10-t1",
                title = "Mole Concept (Finish)",
                subject = "Chemistry",
                grade = "11th",
                phase = "Phase 1",
                targetDetails = "Finish remaining hours of 10h 37m + PYQ Marathon",
                totalDuration = "10h 37m completed",
                lectures = null,
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d10-t2",
                title = "Solution",
                subject = "Chemistry",
                grade = "12th",
                phase = "Phase 2",
                targetDetails = "Lectures 8–12: Osmotic Pressure, Van't Hoff factor",
                totalDuration = null,
                lectures = "Lec 8–12",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day11(): JeeDayPlan = JeeDayPlan(
        dayNumber = 11,
        month = 1,
        week = 2,
        dayOfWeek = "Thu",
        theme = "Electrostatics Gauss Law & Maths 11 finish",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d11-t1",
                title = "Electrostatics",
                subject = "Physics",
                grade = "12th",
                phase = "Phase 1",
                targetDetails = "Lectures 7–11: Gauss Law & Applications",
                totalDuration = null,
                lectures = "Lec 7–11",
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d11-t2",
                title = "Basic Maths (Finish)",
                subject = "Mathematics",
                grade = "11th",
                phase = "Phase 2",
                targetDetails = "Remaining to finish 8h 35m + Modulus & Graphs",
                totalDuration = "8h 35m completed",
                lectures = null,
                isKeyMilestone = false
            ),
        ),
    )

    private fun day12(): JeeDayPlan = JeeDayPlan(
        dayNumber = 12,
        month = 1,
        week = 2,
        dayOfWeek = "Fri",
        theme = "Electrostatics Potential & Atomic Structure intro",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d12-t1",
                title = "Electrostatics",
                subject = "Physics",
                grade = "12th",
                phase = "Phase 1",
                targetDetails = "Lectures 12–16: Electric Potential & Work done",
                totalDuration = null,
                lectures = "Lec 12–16",
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d12-t2",
                title = "Atomic Structure",
                subject = "Chemistry",
                grade = "11th",
                phase = "Phase 2",
                targetDetails = "First 3h / 9h 04m total: Bohr model & Photoelectric effect",
                totalDuration = "9h 04m total",
                lectures = null,
                isKeyMilestone = false
            ),
        ),
    )

    private fun day13(): JeeDayPlan = JeeDayPlan(
        dayNumber = 13,
        month = 1,
        week = 2,
        dayOfWeek = "Sat",
        theme = "Matrices finish & Solution finish",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d13-t1",
                title = "Matrices (Finish)",
                subject = "Mathematics",
                grade = "12th",
                phase = "Phase 1",
                targetDetails = "Lectures 9–12: Inverse of matrix & Cayley Hamilton theorem",
                totalDuration = null,
                lectures = "Lec 9–12",
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d13-t2",
                title = "Solution (Finish)",
                subject = "Chemistry",
                grade = "12th",
                phase = "Phase 2",
                targetDetails = "Lectures 13–16: Abnormal molar mass & Complete Revision",
                totalDuration = null,
                lectures = "Lec 13–16",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day14(): JeeDayPlan = JeeDayPlan(
        dayNumber = 14,
        month = 1,
        week = 2,
        dayOfWeek = "Sun",
        theme = "🎯 Week 2 Mock Test & Speed Optimization",
        isMockDay = true,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d14-t1",
                title = "3-Hour JEE Main Mock Test #2",
                subject = "Mock",
                grade = "Special",
                phase = "Mock Test",
                targetDetails = "10:00 AM – 1:00 PM (Strict Timing)",
                totalDuration = "3 Hours",
                lectures = null,
                isKeyMilestone = true
            ),
            JeeTask(
                id = "d14-t2",
                title = "Analysis & Subject Wise Weak Spot Rectification",
                subject = "Revision",
                grade = "Special",
                phase = "Phase 2",
                targetDetails = "Deep analysis of accuracy in Physics & Physical Chemistry",
                totalDuration = "2 Hours",
                lectures = null,
                isKeyMilestone = false
            ),
        ),
        mockDetails = JeeMockDetails(
            title = "JEE Main Mock Test 02",
            timing = "10:00 AM – 1:00 PM",
            description = "Evaluate retention across Electrostatics, Solution, Matrices, and Mole Concept."
        ),
    )

    private fun day15(): JeeDayPlan = JeeDayPlan(
        dayNumber = 15,
        month = 1,
        week = 3,
        dayOfWeek = "Mon",
        theme = "Motion in Straight Line & Sets",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d15-t1",
                title = "Motion in Straight Line",
                subject = "Physics",
                grade = "11th",
                phase = "Phase 1",
                targetDetails = "Target: 3h / 8h 41m total: Kinematics 1D Equations",
                totalDuration = "8h 41m total",
                lectures = null,
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d15-t2",
                title = "Sets",
                subject = "Mathematics",
                grade = "11th",
                phase = "Phase 2",
                targetDetails = "Target: 3h / 6h 03m total: Venn diagrams & set operations",
                totalDuration = "6h 03m total",
                lectures = null,
                isKeyMilestone = false
            ),
        ),
    )

    private fun day16(): JeeDayPlan = JeeDayPlan(
        dayNumber = 16,
        month = 1,
        week = 3,
        dayOfWeek = "Tue",
        theme = "Vectors & Relations & Functions intro",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d16-t1",
                title = "Vectors",
                subject = "Physics",
                grade = "11th",
                phase = "Phase 1",
                targetDetails = "Target: 3h / 6h 17m total: Dot & Cross product applications",
                totalDuration = "6h 17m total",
                lectures = null,
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d16-t2",
                title = "Relations & Functions",
                subject = "Mathematics",
                grade = "12th",
                phase = "Phase 2",
                targetDetails = "Lectures 1–5: Domain, Range & Types of Relations",
                totalDuration = null,
                lectures = "Lec 1–5",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day17(): JeeDayPlan = JeeDayPlan(
        dayNumber = 17,
        month = 1,
        week = 3,
        dayOfWeek = "Wed",
        theme = "Atomic Structure & Chemical Kinetics intro",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d17-t1",
                title = "Atomic Structure",
                subject = "Chemistry",
                grade = "11th",
                phase = "Phase 1",
                targetDetails = "Next 3h video: Quantum numbers & electronic configuration",
                totalDuration = "9h 04m total",
                lectures = null,
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d17-t2",
                title = "Chemical Kinetics",
                subject = "Chemistry",
                grade = "12th",
                phase = "Phase 2",
                targetDetails = "Lectures 1–4: Rate of reaction & Order of reaction",
                totalDuration = null,
                lectures = "Lec 1–4",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day18(): JeeDayPlan = JeeDayPlan(
        dayNumber = 18,
        month = 1,
        week = 3,
        dayOfWeek = "Thu",
        theme = "Electrostatics finish & Sets finish",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d18-t1",
                title = "Electrostatics (Finish)",
                subject = "Physics",
                grade = "12th",
                phase = "Phase 1",
                targetDetails = "Lectures 17–23: Field due to continuous charge & Finish",
                totalDuration = null,
                lectures = "Lec 17–23",
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d18-t2",
                title = "Sets (Finish)",
                subject = "Mathematics",
                grade = "11th",
                phase = "Phase 2",
                targetDetails = "Remaining to finish 6h 03m + PYQs",
                totalDuration = "6h 03m completed",
                lectures = null,
                isKeyMilestone = false
            ),
        ),
    )

    private fun day19(): JeeDayPlan = JeeDayPlan(
        dayNumber = 19,
        month = 1,
        week = 3,
        dayOfWeek = "Fri",
        theme = "Electric Potential, Dipole & Thermodynamics",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d19-t1",
                title = "Electric Potential, Dipole & Conductor",
                subject = "Physics",
                grade = "12th",
                phase = "Phase 1",
                targetDetails = "Lectures 1–5: Dipole field & Potential energy",
                totalDuration = null,
                lectures = "Lec 1–5",
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d19-t2",
                title = "Thermodynamics",
                subject = "Chemistry",
                grade = "11th",
                phase = "Phase 2",
                targetDetails = "Target: 3h / 11h 56m total: First Law & Enthalpy calculations",
                totalDuration = "11h 56m total",
                lectures = null,
                isKeyMilestone = false
            ),
        ),
    )

    private fun day20(): JeeDayPlan = JeeDayPlan(
        dayNumber = 20,
        month = 1,
        week = 3,
        dayOfWeek = "Sat",
        theme = "Relations & Functions & Chemical Kinetics",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d20-t1",
                title = "Relations & Functions",
                subject = "Mathematics",
                grade = "12th",
                phase = "Phase 1",
                targetDetails = "Lectures 6–12: Composite functions & Invertible functions",
                totalDuration = null,
                lectures = "Lec 6–12",
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d20-t2",
                title = "Chemical Kinetics",
                subject = "Chemistry",
                grade = "12th",
                phase = "Phase 2",
                targetDetails = "Lectures 5–9: First order kinetics & Integrated rate law",
                totalDuration = null,
                lectures = "Lec 5–9",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day21(): JeeDayPlan = JeeDayPlan(
        dayNumber = 21,
        month = 1,
        week = 3,
        dayOfWeek = "Sun",
        theme = "🎯 Week 3 Mock Test & Strategy Review",
        isMockDay = true,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d21-t1",
                title = "3-Hour JEE Main Mock Test #3",
                subject = "Mock",
                grade = "Special",
                phase = "Mock Test",
                targetDetails = "10:00 AM – 1:00 PM (Real Test Environment)",
                totalDuration = "3 Hours",
                lectures = null,
                isKeyMilestone = true
            ),
            JeeTask(
                id = "d21-t2",
                title = "Mock 3 Comprehensive Diagnostics",
                subject = "Revision",
                grade = "Special",
                phase = "Phase 2",
                targetDetails = "Detailed analysis of negative marks and time distribution",
                totalDuration = "2 Hours",
                lectures = null,
                isKeyMilestone = false
            ),
        ),
        mockDetails = JeeMockDetails(
            title = "JEE Main Mock Test 03",
            timing = "10:00 AM – 1:00 PM",
            description = "Includes 1D kinematics, Vectors, Sets, Chemical Kinetics, and Electrostatics."
        ),
    )

    private fun day22(): JeeDayPlan = JeeDayPlan(
        dayNumber = 22,
        month = 1,
        week = 4,
        dayOfWeek = "Mon",
        theme = "Motion in Straight Line finish & R&F continuation",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d22-t1",
                title = "Motion in Straight Line (Finish)",
                subject = "Physics",
                grade = "11th",
                phase = "Phase 1",
                targetDetails = "Remaining to finish 8h 41m + Relative motion in 1D",
                totalDuration = "8h 41m completed",
                lectures = null,
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d22-t2",
                title = "Relations & Functions",
                subject = "Mathematics",
                grade = "12th",
                phase = "Phase 2",
                targetDetails = "Lectures 13–20: Functional equations & Graphs",
                totalDuration = null,
                lectures = "Lec 13–20",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day23(): JeeDayPlan = JeeDayPlan(
        dayNumber = 23,
        month = 1,
        week = 4,
        dayOfWeek = "Tue",
        theme = "Motion in Plane intro & R&F finish",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d23-t1",
                title = "Motion in Plane",
                subject = "Physics",
                grade = "11th",
                phase = "Phase 1",
                targetDetails = "Target: 3h / 10h 48m total: Projectile Motion",
                totalDuration = "10h 48m total",
                lectures = null,
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d23-t2",
                title = "Relations & Functions (Finish)",
                subject = "Mathematics",
                grade = "12th",
                phase = "Phase 2",
                targetDetails = "Lectures 21–29 / Finish: Advanced JEE PYQs",
                totalDuration = null,
                lectures = "Lec 21–29",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day24(): JeeDayPlan = JeeDayPlan(
        dayNumber = 24,
        month = 1,
        week = 4,
        dayOfWeek = "Wed",
        theme = "Thermodynamics & Chemical Kinetics finish",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d24-t1",
                title = "Thermodynamics",
                subject = "Chemistry",
                grade = "11th",
                phase = "Phase 1",
                targetDetails = "Next 3h video: Entropy, Gibbs Free Energy & Spontaneity",
                totalDuration = "11h 56m total",
                lectures = null,
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d24-t2",
                title = "Chemical Kinetics (Finish)",
                subject = "Chemistry",
                grade = "12th",
                phase = "Phase 2",
                targetDetails = "Lectures 10–14 / Finish: Arrhenius Equation & Catalysis",
                totalDuration = null,
                lectures = "Lec 10–14",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day25(): JeeDayPlan = JeeDayPlan(
        dayNumber = 25,
        month = 1,
        week = 4,
        dayOfWeek = "Thu",
        theme = "Conductors & Quadratic Equations intro",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d25-t1",
                title = "Electric Potential, Dipole & Conductor",
                subject = "Physics",
                grade = "12th",
                phase = "Phase 1",
                targetDetails = "Lectures 6–10: Conductors, Shielding & Capacitance intro",
                totalDuration = null,
                lectures = "Lec 6–10",
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d25-t2",
                title = "Quadratic Equations",
                subject = "Mathematics",
                grade = "11th",
                phase = "Phase 2",
                targetDetails = "Target: 3h / 8h 26m total: Roots relations & Nature of roots",
                totalDuration = "8h 26m total",
                lectures = null,
                isKeyMilestone = false
            ),
        ),
    )

    private fun day26(): JeeDayPlan = JeeDayPlan(
        dayNumber = 26,
        month = 1,
        week = 4,
        dayOfWeek = "Fri",
        theme = "Dipole & Conductor finish & Redox Reactions intro",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d26-t1",
                title = "Electric Potential, Dipole & Conductor (Finish)",
                subject = "Physics",
                grade = "12th",
                phase = "Phase 1",
                targetDetails = "Lectures 11–14 / Finish + Earthing concepts",
                totalDuration = null,
                lectures = "Lec 11–14",
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d26-t2",
                title = "Redox Reactions",
                subject = "Chemistry",
                grade = "11th",
                phase = "Phase 2",
                targetDetails = "Target: 3h / 6h 31m total: Oxidation states & balancing",
                totalDuration = "6h 31m total",
                lectures = null,
                isKeyMilestone = false
            ),
        ),
    )

    private fun day27(): JeeDayPlan = JeeDayPlan(
        dayNumber = 27,
        month = 1,
        week = 4,
        dayOfWeek = "Sat",
        theme = "Inverse Trig Functions & Electrochemistry intro",
        isMockDay = false,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d27-t1",
                title = "Inverse Trigonometric Functions",
                subject = "Mathematics",
                grade = "12th",
                phase = "Phase 1",
                targetDetails = "Lectures 1–5: Principal values & Graphs",
                totalDuration = null,
                lectures = "Lec 1–5",
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d27-t2",
                title = "Electrochemistry",
                subject = "Chemistry",
                grade = "12th",
                phase = "Phase 2",
                targetDetails = "Lectures 1–5: Galvanic cells & Nernst Equation",
                totalDuration = null,
                lectures = "Lec 1–5",
                isKeyMilestone = false
            ),
        ),
    )

    private fun day28(): JeeDayPlan = JeeDayPlan(
        dayNumber = 28,
        month = 1,
        week = 4,
        dayOfWeek = "Sun",
        theme = "🎯 Week 4 Mock Test (Month 1 Checkpoint)",
        isMockDay = true,
        isBufferDay = false,
        tasks = listOf(
            JeeTask(
                id = "d28-t1",
                title = "3-Hour JEE Main Mock Test #4",
                subject = "Mock",
                grade = "Special",
                phase = "Mock Test",
                targetDetails = "10:00 AM – 1:00 PM (Month 1 Grand Assessment)",
                totalDuration = "3 Hours",
                lectures = null,
                isKeyMilestone = true
            ),
            JeeTask(
                id = "d28-t2",
                title = "Month 1 Performance Audit",
                subject = "Revision",
                grade = "Special",
                phase = "Phase 2",
                targetDetails = "Review all 4 mock test trends & prioritize backlog in buffer days",
                totalDuration = "2 Hours",
                lectures = null,
                isKeyMilestone = false
            ),
        ),
        mockDetails = JeeMockDetails(
            title = "JEE Main Month 1 Grand Mock",
            timing = "10:00 AM – 1:00 PM",
            description = "Comprehensive test of all Month 1 syllabus (Electrostatics, Solutions, Kinetics, Basic Maths, Relations)."
        ),
    )

    private fun day29(): JeeDayPlan = JeeDayPlan(
        dayNumber = 29,
        month = 1,
        week = 4,
        dayOfWeek = "Mon",
        theme = "🛡️ Buffer Day 1 - Revision & Backlog Catchup",
        isMockDay = false,
        isBufferDay = true,
        tasks = listOf(
            JeeTask(
                id = "d29-t1",
                title = "Month 1 Physics & Chemistry Backlog Clearing",
                subject = "Buffer",
                grade = "Special",
                phase = "Buffer",
                targetDetails = "Catch up on missed video lectures or pending DPPs in Kinematics / Thermodynamics",
                totalDuration = "4 Hours",
                lectures = null,
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d29-t2",
                title = "Mathematics Formula Sheet & Problem Drill",
                subject = "Revision",
                grade = "Special",
                phase = "Phase 2",
                targetDetails = "Determinants, Matrices, Relations & Functions high-yield problem solving",
                totalDuration = "3 Hours",
                lectures = null,
                isKeyMilestone = false
            ),
        ),
    )

    private fun day30(): JeeDayPlan = JeeDayPlan(
        dayNumber = 30,
        month = 1,
        week = 4,
        dayOfWeek = "Tue",
        theme = "🛡️ Buffer Day 2 - Month 1 Consolidation",
        isMockDay = false,
        isBufferDay = true,
        tasks = listOf(
            JeeTask(
                id = "d30-t1",
                title = "Month 1 Short Notes & Formula Memorization",
                subject = "Buffer",
                grade = "Special",
                phase = "Buffer",
                targetDetails = "Consolidate 1-page summary sheets for all 10 chapters completed in Month 1",
                totalDuration = "4 Hours",
                lectures = null,
                isKeyMilestone = false
            ),
            JeeTask(
                id = "d30-t2",
                title = "PYQ Speed Test (Timed 30 Questions)",
                subject = "Revision",
                grade = "Special",
                phase = "Phase 2",
                targetDetails = "30 mixed questions from Month 1 chapters under 60-minute time constraint",
                totalDuration = "2 Hours",
                lectures = null,
                isKeyMilestone = false
            ),
        ),
    )

}
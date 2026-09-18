package cz.demo.caselaw.domain;

import java.util.List;

/** Final AI output: Advocatus Diaboli arguments, judge verdict, verified citations. */
public record Answer(
        List<Argument> forArguments,
        List<Argument> againstArguments,
        String verdict,
        Side strongerSide,
        List<Citation> citations,
        int verifiedCount,
        int unverifiedCount,
        int attempts,
        String model,
        boolean mock
) {}

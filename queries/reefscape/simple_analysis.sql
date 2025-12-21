--
-- Runs a simple analysis on all teams.
--
-- Compute a "weighted" score for each team based upon their full
-- coral score favoring later matches.
--
SELECT teamnumber,
       SUM(matchnumber * (CLOT + CLTT + CLThT + CLFT)) AS final_score,

-- Show breakdown of each level of coral, weighted
       SUM(matchnumber * CLOT) AS weighted_L1,
       SUM(matchnumber * CLTT) AS weighted_L2,
       SUM(matchnumber * CLThT) AS weighted_L3,
       SUM(matchnumber * CLFT) AS weighted_L4,

-- Show breakdown of each level of coral, unweighted
       SUM(CLOT) AS total_L1,
       SUM(CLTT) AS total_L2,
       SUM(CLThT) AS total_L3,
       SUM(CLFT) AS total_L4

FROM stand_scouting
GROUP BY teamnumber
ORDER BY final_score DESC


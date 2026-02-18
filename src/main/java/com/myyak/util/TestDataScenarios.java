package com.myyak.util;

import com.myyak.web.dto.AnalysisDTO.AnalysisResponseDTO;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * AI 테스트 분석용 시나리오 데이터
 * 4개 증상 카테고리별 Mock 약물 및 복약 기록 데이터를 제공합니다.
 */
public class TestDataScenarios {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 증상 목록에서 가장 적합한 시나리오를 선택합니다.
     * 카테고리별 매칭 증상 수를 세고, 가장 많은 카테고리를 선택합니다.
     * 동점일 경우 우선순위: CARDIOVASCULAR > DIGESTIVE > MUSCULOSKELETAL > NEUROPSYCHIATRIC
     *
     * @param symptoms 사용자가 선택한 증상 목록
     * @return 해당 카테고리의 TestAnalysisScenario
     */
    public static TestAnalysisScenario getScenarioForSymptoms(List<String> symptoms) {
        if (symptoms == null || symptoms.isEmpty()) {
            return buildCardiovascularScenario();
        }

        Set<String> symptomSet = new HashSet<>(symptoms);

        int cardiovascularCount = countMatches(symptomSet, Set.of("headache", "dizziness", "fatigue"));
        int digestiveCount = countMatches(symptomSet, Set.of("nausea", "stomachache", "indigestion"));
        int musculoskeletalCount = countMatches(symptomSet, Set.of("muscle_pain", "joint_pain", "rash"));
        int neuropsychiatricCount = countMatches(symptomSet, Set.of("insomnia", "anxiety", "cough"));

        // 우선순위 순서로 비교
        int maxCount = Math.max(Math.max(cardiovascularCount, digestiveCount),
                Math.max(musculoskeletalCount, neuropsychiatricCount));

        if (maxCount == 0) {
            return buildCardiovascularScenario();
        }

        if (cardiovascularCount == maxCount) return buildCardiovascularScenario();
        if (digestiveCount == maxCount) return buildDigestiveScenario();
        if (musculoskeletalCount == maxCount) return buildMusculoskeletalScenario();
        return buildNeuropsychiatricScenario();
    }

    private static int countMatches(Set<String> symptoms, Set<String> categorySymptoms) {
        int count = 0;
        for (String s : symptoms) {
            if (categorySymptoms.contains(s)) count++;
        }
        return count;
    }

    // ===== CARDIOVASCULAR =====

    private static TestAnalysisScenario buildCardiovascularScenario() {
        List<TestAnalysisScenario.MockMedication> meds = List.of(
                TestAnalysisScenario.MockMedication.builder()
                        .id(1001L).name("노바스크정 5mg").ingredient("암로디핀베실산염")
                        .timings(List.of("MORNING")).build(),
                TestAnalysisScenario.MockMedication.builder()
                        .id(1002L).name("아스피린프로텍트 100mg").ingredient("아세틸살리실산")
                        .timings(List.of("MORNING")).build(),
                TestAnalysisScenario.MockMedication.builder()
                        .id(1003L).name("리피토정 10mg").ingredient("아토르바스타틴칼슘")
                        .timings(List.of("DINNER")).build()
        );

        String userPrompt = """
                {
                  "user_medications": [
                    {"item_seq": "MOCK001", "display_name": "노바스크정 5mg", "ingredient_kr": "암로디핀베실산염", "efficacy_summary": "고혈압 치료, 혈관 이완을 통한 혈압 강하"},
                    {"item_seq": "MOCK002", "display_name": "아스피린프로텍트 100mg", "ingredient_kr": "아세틸살리실산", "efficacy_summary": "혈전 예방, 혈소판 응집 억제"},
                    {"item_seq": "MOCK003", "display_name": "리피토정 10mg", "ingredient_kr": "아토르바스타틴칼슘", "efficacy_summary": "고지혈증 치료, 콜레스테롤 합성 억제"}
                  ],
                  "user_supplements": [],
                  "drug_interactions": [],
                  "food_interactions": [
                    {"drug_item_seq": "MOCK001", "ingredient_name": "암로디핀베실산염", "food_name": "자몽", "food_icon": "🍊", "interaction_level": "HIGH", "interaction_data": "CYP3A4 효소 억제로 약물 혈중 농도 증가 가능"},
                    {"drug_item_seq": "MOCK003", "ingredient_name": "아토르바스타틴칼슘", "food_name": "자몽", "food_icon": "🍊", "interaction_level": "HIGH", "interaction_data": "CYP3A4 효소 억제로 스타틴 혈중 농도가 크게 증가하여 근육 부작용 위험 증가"},
                    {"drug_item_seq": "MOCK002", "ingredient_name": "아세틸살리실산", "food_name": "알코올", "food_icon": "🍺", "interaction_level": "HIGH", "interaction_data": "위장 출혈 위험 증가, 아스피린의 위장 점막 자극 효과 강화"},
                    {"drug_item_seq": "MOCK001", "ingredient_name": "암로디핀베실산염", "food_name": "알코올", "food_icon": "🍺", "interaction_level": "MEDIUM", "interaction_data": "혈압 강하 효과가 과도해질 수 있음"}
                  ]
                }""";

        String[] notes = {
                "오늘 두통이 심했다",
                "어지러움이 있었다",
                "약 빼먹은 다음 날이라 그런지 컨디션이 안 좋다",
                "혈압이 좀 높게 나왔다",
                "오후에 머리가 띵했다",
                "아침에 일어나기 힘들었다",
                "오늘은 컨디션이 괜찮다",
                "산책 후 기분이 좋아졌다",
                "두통약을 추가로 먹었다",
                "어깨가 뻐근하고 두통이 같이 왔다",
                "피곤해서 일찍 잤다",
                "오늘은 활력이 좋았다"
        };

        return TestAnalysisScenario.builder()
                .categoryName("CARDIOVASCULAR")
                .medications(meds)
                .userPromptJson(userPrompt)
                .patternPromptJsonSupplier(tempNotesJson -> buildPatternPromptJson(meds, notes, tempNotesJson))
                .dailyConditionsSupplier(() -> buildMockDailyConditions(meds, notes))
                .build();
    }

    // ===== DIGESTIVE =====

    private static TestAnalysisScenario buildDigestiveScenario() {
        List<TestAnalysisScenario.MockMedication> meds = List.of(
                TestAnalysisScenario.MockMedication.builder()
                        .id(2001L).name("메트포르민 500mg").ingredient("메트포르민염산염")
                        .timings(List.of("MORNING", "DINNER")).build(),
                TestAnalysisScenario.MockMedication.builder()
                        .id(2002L).name("넥시움정 20mg").ingredient("에소메프라졸마그네슘")
                        .timings(List.of("MORNING")).build(),
                TestAnalysisScenario.MockMedication.builder()
                        .id(2003L).name("가스모틴정 5mg").ingredient("모사프리드시트르산염")
                        .timings(List.of("MORNING", "LUNCH", "DINNER")).build()
        );

        String userPrompt = """
                {
                  "user_medications": [
                    {"item_seq": "MOCK011", "display_name": "메트포르민 500mg", "ingredient_kr": "메트포르민염산염", "efficacy_summary": "제2형 당뇨병 치료, 간의 포도당 생산 억제 및 인슐린 감수성 개선"},
                    {"item_seq": "MOCK012", "display_name": "넥시움정 20mg", "ingredient_kr": "에소메프라졸마그네슘", "efficacy_summary": "위식도역류질환 및 위궤양 치료, 위산 분비 억제"},
                    {"item_seq": "MOCK013", "display_name": "가스모틴정 5mg", "ingredient_kr": "모사프리드시트르산염", "efficacy_summary": "위장운동 촉진, 소화불량 및 위장 운동 기능 개선"}
                  ],
                  "user_supplements": [],
                  "drug_interactions": [],
                  "food_interactions": [
                    {"drug_item_seq": "MOCK011", "ingredient_name": "메트포르민염산염", "food_name": "알코올", "food_icon": "🍺", "interaction_level": "HIGH", "interaction_data": "유산산증(젖산산증) 위험 증가, 알코올이 간의 포도당 생산에 영향"},
                    {"drug_item_seq": "MOCK011", "ingredient_name": "메트포르민염산염", "food_name": "고탄수화물 식품", "food_icon": "🍚", "interaction_level": "MEDIUM", "interaction_data": "혈당 급격한 상승으로 약물 효과 감소 가능"},
                    {"drug_item_seq": "MOCK012", "ingredient_name": "에소메프라졸마그네슘", "food_name": "카페인 음료", "food_icon": "☕", "interaction_level": "MEDIUM", "interaction_data": "카페인이 위산 분비를 촉진하여 약효 감소 가능"}
                  ]
                }""";

        String[] notes = {
                "속이 안 좋다",
                "소화가 잘 안 된다",
                "약을 빈속에 먹었더니 메스꺼움이 있었다",
                "식후에 복부 팽만감이 있었다",
                "아침에 속이 쓰렸다",
                "오늘은 소화가 잘 됐다",
                "과식 후 더부룩했다",
                "약 먹고 속이 좀 편해졌다",
                "배에 가스가 많이 찬다",
                "저녁 늦게 먹었더니 속이 안 좋다",
                "오늘은 위장 상태가 좋다",
                "메스꺼움이 조금 있었다"
        };

        return TestAnalysisScenario.builder()
                .categoryName("DIGESTIVE")
                .medications(meds)
                .userPromptJson(userPrompt)
                .patternPromptJsonSupplier(tempNotesJson -> buildPatternPromptJson(meds, notes, tempNotesJson))
                .dailyConditionsSupplier(() -> buildMockDailyConditions(meds, notes))
                .build();
    }

    // ===== MUSCULOSKELETAL =====

    private static TestAnalysisScenario buildMusculoskeletalScenario() {
        List<TestAnalysisScenario.MockMedication> meds = List.of(
                TestAnalysisScenario.MockMedication.builder()
                        .id(3001L).name("프레드니솔론정 5mg").ingredient("프레드니솔론")
                        .timings(List.of("MORNING")).build(),
                TestAnalysisScenario.MockMedication.builder()
                        .id(3002L).name("셀레브렉스 200mg").ingredient("셀레콕시브")
                        .timings(List.of("MORNING", "DINNER")).build(),
                TestAnalysisScenario.MockMedication.builder()
                        .id(3003L).name("메토트렉세이트정 2.5mg").ingredient("메토트렉세이트")
                        .timings(List.of("MORNING")).build()
        );

        String userPrompt = """
                {
                  "user_medications": [
                    {"item_seq": "MOCK021", "display_name": "프레드니솔론정 5mg", "ingredient_kr": "프레드니솔론", "efficacy_summary": "항염증 및 면역 억제, 류마티스 관절염 등 자가면역질환 치료"},
                    {"item_seq": "MOCK022", "display_name": "셀레브렉스 200mg", "ingredient_kr": "셀레콕시브", "efficacy_summary": "COX-2 선택적 억제, 관절염 통증 및 염증 완화"},
                    {"item_seq": "MOCK023", "display_name": "메토트렉세이트정 2.5mg", "ingredient_kr": "메토트렉세이트", "efficacy_summary": "면역 조절, 류마티스 관절염의 질병 활성도 억제"}
                  ],
                  "user_supplements": [],
                  "drug_interactions": [],
                  "food_interactions": [
                    {"drug_item_seq": "MOCK021", "ingredient_name": "프레드니솔론", "food_name": "알코올", "food_icon": "🍺", "interaction_level": "MEDIUM", "interaction_data": "위장 점막 자극 증가, 소화성 궤양 위험 상승"},
                    {"drug_item_seq": "MOCK022", "ingredient_name": "셀레콕시브", "food_name": "알코올", "food_icon": "🍺", "interaction_level": "HIGH", "interaction_data": "위장 출혈 위험 증가, NSAIDs와 알코올의 상승 작용"},
                    {"drug_item_seq": "MOCK023", "ingredient_name": "메토트렉세이트", "food_name": "엽산이 풍부한 식품", "food_icon": "🥬", "interaction_level": "MEDIUM", "interaction_data": "과도한 엽산 섭취 시 메토트렉세이트 효과에 영향 가능, 적정량 유지 권장"},
                    {"drug_item_seq": "MOCK023", "ingredient_name": "메토트렉세이트", "food_name": "카페인 음료", "food_icon": "☕", "interaction_level": "MEDIUM", "interaction_data": "카페인이 메토트렉세이트의 효과를 감소시킬 수 있음"}
                  ]
                }""";

        String[] notes = {
                "관절이 뻣뻣하다",
                "근육통이 있다",
                "피부에 발진이 조금 나타났다",
                "아침에 관절 강직이 심했다",
                "손가락 관절이 붓고 아프다",
                "오늘은 통증이 덜하다",
                "약 먹고 나서 조금 나아졌다",
                "비 오니까 관절이 더 아프다",
                "운동 후 근육통이 심해졌다",
                "피로감이 심하다",
                "관절 부종이 줄었다",
                "오늘은 몸이 가벼웠다"
        };

        return TestAnalysisScenario.builder()
                .categoryName("MUSCULOSKELETAL")
                .medications(meds)
                .userPromptJson(userPrompt)
                .patternPromptJsonSupplier(tempNotesJson -> buildPatternPromptJson(meds, notes, tempNotesJson))
                .dailyConditionsSupplier(() -> buildMockDailyConditions(meds, notes))
                .build();
    }

    // ===== NEUROPSYCHIATRIC =====

    private static TestAnalysisScenario buildNeuropsychiatricScenario() {
        List<TestAnalysisScenario.MockMedication> meds = List.of(
                TestAnalysisScenario.MockMedication.builder()
                        .id(4001L).name("졸피뎀CR 6.25mg").ingredient("졸피뎀타르타르산염")
                        .timings(List.of("BEDTIME")).build(),
                TestAnalysisScenario.MockMedication.builder()
                        .id(4002L).name("렉사프로정 10mg").ingredient("에스시탈로프람옥살산염")
                        .timings(List.of("MORNING")).build(),
                TestAnalysisScenario.MockMedication.builder()
                        .id(4003L).name("몬테루카스트정 10mg").ingredient("몬테루카스트나트륨")
                        .timings(List.of("BEDTIME")).build()
        );

        String userPrompt = """
                {
                  "user_medications": [
                    {"item_seq": "MOCK031", "display_name": "졸피뎀CR 6.25mg", "ingredient_kr": "졸피뎀타르타르산염", "efficacy_summary": "불면증 치료, GABA 수용체 작용을 통한 수면 유도"},
                    {"item_seq": "MOCK032", "display_name": "렉사프로정 10mg", "ingredient_kr": "에스시탈로프람옥살산염", "efficacy_summary": "우울증 및 불안장애 치료, 세로토닌 재흡수 억제(SSRI)"},
                    {"item_seq": "MOCK033", "display_name": "몬테루카스트정 10mg", "ingredient_kr": "몬테루카스트나트륨", "efficacy_summary": "천식 및 알레르기 비염 치료, 류코트리엔 수용체 길항"}
                  ],
                  "user_supplements": [],
                  "drug_interactions": [],
                  "food_interactions": [
                    {"drug_item_seq": "MOCK031", "ingredient_name": "졸피뎀타르타르산염", "food_name": "알코올", "food_icon": "🍺", "interaction_level": "HIGH", "interaction_data": "중추신경 억제 효과 증강, 과도한 진정 및 호흡 억제 위험"},
                    {"drug_item_seq": "MOCK032", "ingredient_name": "에스시탈로프람옥살산염", "food_name": "자몽", "food_icon": "🍊", "interaction_level": "MEDIUM", "interaction_data": "CYP3A4 효소 억제로 약물 혈중 농도 증가 가능"},
                    {"drug_item_seq": "MOCK031", "ingredient_name": "졸피뎀타르타르산염", "food_name": "카페인 음료", "food_icon": "☕", "interaction_level": "MEDIUM", "interaction_data": "카페인의 각성 효과가 졸피뎀의 수면 유도 효과를 감소시킬 수 있음"},
                    {"drug_item_seq": "MOCK032", "ingredient_name": "에스시탈로프람옥살산염", "food_name": "알코올", "food_icon": "🍺", "interaction_level": "HIGH", "interaction_data": "중추신경 억제 효과 상승, 판단력 및 운동 기능 저하"}
                  ]
                }""";

        String[] notes = {
                "어젯밤 잠을 잘 못 잤다",
                "불안감이 좀 있었다",
                "약 먹고 나서 좀 졸렸다",
                "새벽에 깨서 다시 잠들기 어려웠다",
                "오늘은 기분이 괜찮았다",
                "카페인을 줄였더니 수면이 나아졌다",
                "기침이 조금 있었다",
                "아침에 머리가 멍한 느낌이었다",
                "불안감 없이 하루를 보냈다",
                "잠드는 데 시간이 오래 걸렸다",
                "낮에 졸음이 심했다",
                "오늘은 숙면했다"
        };

        return TestAnalysisScenario.builder()
                .categoryName("NEUROPSYCHIATRIC")
                .medications(meds)
                .userPromptJson(userPrompt)
                .patternPromptJsonSupplier(tempNotesJson -> buildPatternPromptJson(meds, notes, tempNotesJson))
                .dailyConditionsSupplier(() -> buildMockDailyConditions(meds, notes))
                .build();
    }

    // ===== Pattern Prompt Builder =====

    /**
     * 30일간의 패턴 분석 프롬프트 JSON을 동적으로 생성합니다.
     *
     * 데이터 패턴:
     * - 전체 복약률 ~87%, 평일 ~92%, 주말 ~73%
     * - 2개의 연속 미복용 클러스터 (day 8-10, day 21-23)
     * - 저녁 복용 누락이 아침의 2배
     * - 컨디션 점수: 기준선 6-7, 미복용 후 3-4
     * - 건강 메모: 30일 중 ~12일에 존재
     */
    private static String buildPatternPromptJson(
            List<TestAnalysisScenario.MockMedication> meds,
            String[] healthNotes,
            String temporaryNotesJson) {

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(29);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // analysis_period
        sb.append("  \"analysis_period\": {\n");
        sb.append("    \"start_date\": \"").append(startDate.format(DATE_FMT)).append("\",\n");
        sb.append("    \"end_date\": \"").append(endDate.format(DATE_FMT)).append("\",\n");
        sb.append("    \"total_days\": 30\n");
        sb.append("  },\n");

        // medications
        sb.append("  \"medications\": [\n");
        for (int i = 0; i < meds.size(); i++) {
            TestAnalysisScenario.MockMedication m = meds.get(i);
            sb.append("    {\"id\": ").append(m.getId())
                    .append(", \"name\": \"").append(m.getName())
                    .append("\", \"ingredient\": \"").append(m.getIngredient()).append("\"}");
            if (i < meds.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // daily_records
        sb.append("  \"daily_records\": [\n");

        // Pre-define which days have missed doses, condition scores, and notes
        // Days 0-29 (0 = startDate, 29 = endDate)
        Set<Integer> missedDayClusters = Set.of(8, 9, 10, 21, 22, 23);
        Set<Integer> weekendMissedSomeDays = Set.of(5, 6, 12, 13, 19, 20, 26, 27);
        // Note indices for the 12 notes
        Set<Integer> noteDays = Set.of(1, 3, 5, 8, 9, 11, 14, 17, 20, 22, 25, 28);

        int totalTaken = 0;
        int totalSkipped = 0;
        int totalScheduled = 0;
        int noteIdx = 0;

        List<Integer> conditionScores = new ArrayList<>();

        for (int dayOffset = 0; dayOffset < 30; dayOffset++) {
            LocalDate date = startDate.plusDays(dayOffset);
            DayOfWeek dow = date.getDayOfWeek();
            boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            boolean isMissedCluster = missedDayClusters.contains(dayOffset);

            sb.append("    {\n");
            sb.append("      \"date\": \"").append(date.format(DATE_FMT)).append("\",\n");
            sb.append("      \"day_of_week\": \"").append(dow.name()).append("\",\n");

            // Calculate intake status for each medication
            List<Map<String, Object>> intakes = new ArrayList<>();
            int dayTaken = 0;
            int daySkipped = 0;
            Map<String, Integer> timingBreakdown = new LinkedHashMap<>();
            timingBreakdown.put("MORNING", 0);
            timingBreakdown.put("LUNCH", 0);
            timingBreakdown.put("DINNER", 0);
            timingBreakdown.put("BEDTIME", 0);

            for (TestAnalysisScenario.MockMedication med : meds) {
                for (String timing : med.getTimings()) {
                    boolean taken;
                    if (isMissedCluster) {
                        // During missed clusters: mostly skip, especially evening
                        taken = timing.equals("MORNING") && dayOffset != 9;
                    } else if (isWeekend && weekendMissedSomeDays.contains(dayOffset)) {
                        // Weekend: sometimes miss evening doses
                        taken = !timing.equals("DINNER") && !timing.equals("BEDTIME");
                    } else {
                        // Normal weekday: very high adherence, occasional evening miss
                        taken = !(timing.equals("DINNER") && dayOffset % 7 == 4);
                    }

                    Map<String, Object> intake = new LinkedHashMap<>();
                    intake.put("medication_id", med.getId());
                    intake.put("medication_name", med.getName());
                    intake.put("timing", timing);
                    intake.put("status", taken ? "TAKEN" : "SKIPPED");

                    if (taken) {
                        String baseTime = switch (timing) {
                            case "MORNING" -> String.format("08:%02d", 0 + (dayOffset % 30));
                            case "LUNCH" -> String.format("12:%02d", 10 + (dayOffset % 20));
                            case "DINNER" -> String.format("18:%02d", 30 + (dayOffset % 25));
                            case "BEDTIME" -> String.format("22:%02d", 0 + (dayOffset % 30));
                            default -> "09:00";
                        };
                        intake.put("taken_at", baseTime);
                        dayTaken++;
                        timingBreakdown.put(timing, timingBreakdown.getOrDefault(timing, 0) + 1);
                    } else {
                        intake.put("taken_at", null);
                        daySkipped++;
                    }

                    intakes.add(intake);
                }
            }

            int dayTotal = dayTaken + daySkipped;
            double adherenceRate = dayTotal > 0 ? Math.round((double) dayTaken / dayTotal * 100.0) : 0;

            totalTaken += dayTaken;
            totalSkipped += daySkipped;
            totalScheduled += dayTotal;

            sb.append("      \"taken_count\": ").append(dayTaken).append(",\n");
            sb.append("      \"skipped_count\": ").append(daySkipped).append(",\n");
            sb.append("      \"total_scheduled\": ").append(dayTotal).append(",\n");
            sb.append("      \"adherence_rate\": ").append((int) adherenceRate).append(",\n");

            // timing_breakdown
            sb.append("      \"timing_breakdown\": {");
            sb.append("\"MORNING\": ").append(timingBreakdown.get("MORNING")).append(", ");
            sb.append("\"LUNCH\": ").append(timingBreakdown.get("LUNCH")).append(", ");
            sb.append("\"DINNER\": ").append(timingBreakdown.get("DINNER")).append(", ");
            sb.append("\"BEDTIME\": ").append(timingBreakdown.get("BEDTIME"));
            sb.append("},\n");

            // intakes array
            sb.append("      \"intakes\": [\n");
            for (int i = 0; i < intakes.size(); i++) {
                Map<String, Object> intake = intakes.get(i);
                sb.append("        {");
                sb.append("\"medication_id\": ").append(intake.get("medication_id")).append(", ");
                sb.append("\"medication_name\": \"").append(intake.get("medication_name")).append("\", ");
                sb.append("\"timing\": \"").append(intake.get("timing")).append("\", ");
                sb.append("\"status\": \"").append(intake.get("status")).append("\", ");
                if (intake.get("taken_at") != null) {
                    sb.append("\"taken_at\": \"").append(intake.get("taken_at")).append("\"");
                } else {
                    sb.append("\"taken_at\": null");
                }
                sb.append("}");
                if (i < intakes.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("      ],\n");

            // condition_score
            Integer conditionScore = null;
            if (isMissedCluster) {
                // After missed clusters: low scores
                conditionScore = 3 + (dayOffset % 2);
            } else if (dayOffset > 0 && missedDayClusters.contains(dayOffset - 1)) {
                // Day after missed cluster: still low
                conditionScore = 4 + (dayOffset % 2);
            } else if (noteDays.contains(dayOffset) || dayOffset % 5 == 0) {
                // Normal days with scores
                conditionScore = 6 + (dayOffset % 3);
            }

            if (conditionScore != null) {
                conditionScores.add(conditionScore);
                sb.append("      \"condition_score\": ").append(conditionScore).append(",\n");
            } else {
                sb.append("      \"condition_score\": null,\n");
            }

            // note_content
            if (noteDays.contains(dayOffset) && noteIdx < healthNotes.length) {
                sb.append("      \"note_content\": \"").append(healthNotes[noteIdx]).append("\"\n");
                noteIdx++;
            } else {
                sb.append("      \"note_content\": null\n");
            }

            sb.append("    }");
            if (dayOffset < 29) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // statistics
        double overallAdherenceRate = totalScheduled > 0 ?
                Math.round((double) totalTaken / totalScheduled * 1000.0) / 10.0 : 0;
        double avgCondition = conditionScores.isEmpty() ? 0 :
                Math.round(conditionScores.stream().mapToInt(i -> i).average().orElse(0) * 10.0) / 10.0;
        int minCondition = conditionScores.isEmpty() ? 0 :
                conditionScores.stream().mapToInt(i -> i).min().orElse(0);
        int maxCondition = conditionScores.isEmpty() ? 10 :
                conditionScores.stream().mapToInt(i -> i).max().orElse(10);

        sb.append("  \"statistics\": {\n");
        sb.append("    \"total_taken\": ").append(totalTaken).append(",\n");
        sb.append("    \"total_skipped\": ").append(totalSkipped).append(",\n");
        sb.append("    \"total_scheduled\": ").append(totalScheduled).append(",\n");
        sb.append("    \"overall_adherence_rate\": ").append(overallAdherenceRate).append(",\n");
        sb.append("    \"avg_condition_score\": ").append(avgCondition).append(",\n");
        sb.append("    \"min_condition_score\": ").append(minCondition).append(",\n");
        sb.append("    \"max_condition_score\": ").append(maxCondition).append(",\n");
        sb.append("    \"notes_count\": ").append(conditionScores.size()).append("\n");
        sb.append("  },\n");

        // temporary_notes
        sb.append("  \"temporary_notes\": ").append(temporaryNotesJson != null ? temporaryNotesJson : "[]").append("\n");

        sb.append("}");

        return sb.toString();
    }

    /**
     * Mock dailyConditions 데이터를 직접 생성합니다.
     * buildPatternPromptJson과 동일한 로직으로 condition_score, adherence_rate, note_content를 계산합니다.
     */
    private static List<AnalysisResponseDTO.DailyCondition> buildMockDailyConditions(
            List<TestAnalysisScenario.MockMedication> meds, String[] healthNotes) {

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(29);

        Set<Integer> missedDayClusters = Set.of(8, 9, 10, 21, 22, 23);
        Set<Integer> weekendMissedSomeDays = Set.of(5, 6, 12, 13, 19, 20, 26, 27);
        Set<Integer> noteDays = Set.of(1, 3, 5, 8, 9, 11, 14, 17, 20, 22, 25, 28);

        List<AnalysisResponseDTO.DailyCondition> result = new ArrayList<>();
        int noteIdx = 0;

        for (int dayOffset = 0; dayOffset < 30; dayOffset++) {
            LocalDate date = startDate.plusDays(dayOffset);
            DayOfWeek dow = date.getDayOfWeek();
            boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            boolean isMissedCluster = missedDayClusters.contains(dayOffset);

            // adherenceRate 계산 (buildPatternPromptJson과 동일 로직)
            int dayTaken = 0;
            int dayTotal = 0;
            for (TestAnalysisScenario.MockMedication med : meds) {
                for (String timing : med.getTimings()) {
                    dayTotal++;
                    boolean taken;
                    if (isMissedCluster) {
                        taken = timing.equals("MORNING") && dayOffset != 9;
                    } else if (isWeekend && weekendMissedSomeDays.contains(dayOffset)) {
                        taken = !timing.equals("DINNER") && !timing.equals("BEDTIME");
                    } else {
                        taken = !(timing.equals("DINNER") && dayOffset % 7 == 4);
                    }
                    if (taken) dayTaken++;
                }
            }
            double adherenceRate = dayTotal > 0 ? Math.round((double) dayTaken / dayTotal * 100.0) : 0;

            // conditionScore 계산 (buildPatternPromptJson과 동일 로직)
            Integer conditionScore = null;
            if (isMissedCluster) {
                conditionScore = 3 + (dayOffset % 2);
            } else if (dayOffset > 0 && missedDayClusters.contains(dayOffset - 1)) {
                conditionScore = 4 + (dayOffset % 2);
            } else if (noteDays.contains(dayOffset) || dayOffset % 5 == 0) {
                conditionScore = 6 + (dayOffset % 3);
            }

            // noteContent
            String noteContent = null;
            boolean hasNote = false;
            if (noteDays.contains(dayOffset) && noteIdx < healthNotes.length) {
                noteContent = healthNotes[noteIdx];
                hasNote = true;
                noteIdx++;
            }

            result.add(AnalysisResponseDTO.DailyCondition.builder()
                    .date(date)
                    .conditionScore(conditionScore)
                    .adherenceRate(adherenceRate)
                    .hasNote(hasNote)
                    .content(noteContent)
                    .build());
        }

        return result;
    }
}

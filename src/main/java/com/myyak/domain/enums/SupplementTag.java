package com.myyak.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SupplementTag {

    VITAMIN_A("비타민 A"),
    VITAMIN_B("비타민 B"),
    VITAMIN_C("비타민 C"),
    VITAMIN_D("비타민 D"),
    VITAMIN_E("비타민 E"),
    OMEGA_3("오메가 3"),
    MAGNESIUM("마그네슘"),
    CALCIUM("칼슘"),
    IRON("철분"),
    ZINC("아연"),
    ARGININE("아르기닌"),
    COLLAGEN("콜라겐"),
    PROBIOTICS("유산균"),
    LUTEIN("루테인"),
    COENZYME_Q10("코엔자임Q10"),
    OTHER("기타");

    private final String description;
}

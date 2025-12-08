package com.myyak.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * 식품의약품안전처 의약품 제품 허가정보 API 테스트
 *
 * API 문서: https://www.data.go.kr/data/15095677/openapi.do
 * 엔드포인트: http://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07/getDrugPrdtPrmsnInq07
 */
public class DrugApiTest {

    // 공공데이터포털에서 발급받은 API 키 (인코딩된 키 사용)
    private static final String SERVICE_KEY = "YOUR_API_KEY_HERE";

    public static void main(String[] args) throws Exception {
        // 테스트: "타이레놀" 검색
        String response = searchDrug("타이레놀");
        System.out.println("=== API 응답 ===");
        System.out.println(response);
    }

    /**
     * 약품명으로 의약품 정보 검색
     */
    public static String searchDrug(String itemName) throws Exception {
        StringBuilder urlBuilder = new StringBuilder("http://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07/getDrugPrdtPrmsnInq07");
        urlBuilder.append("?" + URLEncoder.encode("serviceKey", "UTF-8") + "=" + SERVICE_KEY);
        urlBuilder.append("&" + URLEncoder.encode("pageNo", "UTF-8") + "=" + URLEncoder.encode("1", "UTF-8"));
        urlBuilder.append("&" + URLEncoder.encode("numOfRows", "UTF-8") + "=" + URLEncoder.encode("10", "UTF-8"));
        urlBuilder.append("&" + URLEncoder.encode("type", "UTF-8") + "=" + URLEncoder.encode("json", "UTF-8"));
        urlBuilder.append("&" + URLEncoder.encode("item_name", "UTF-8") + "=" + URLEncoder.encode(itemName, "UTF-8"));

        URL url = new URL(urlBuilder.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-type", "application/json");

        int responseCode = conn.getResponseCode();
        System.out.println("Response Code: " + responseCode);

        BufferedReader rd;
        if (responseCode >= 200 && responseCode <= 300) {
            rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        } else {
            rd = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
        }

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) {
            sb.append(line);
        }
        rd.close();
        conn.disconnect();

        return sb.toString();
    }
}

/*
=== 예상 응답 형식 (JSON) ===

{
  "header": {
    "resultCode": "00",
    "resultMsg": "NORMAL SERVICE"
  },
  "body": {
    "pageNo": 1,
    "totalCount": 50,
    "numOfRows": 10,
    "items": [
      {
        "ITEM_SEQ": "200808466",
        "ITEM_NAME": "타이레놀정500밀리그램(아세트아미노펜)",
        "ENTP_NAME": "한국존슨앤드존슨판매(유)",
        "ITEM_PERMIT_DATE": "20080808",
        "CNSGN_MANUF": null,
        "ETC_OTC_CODE": "전문의약품",
        "CHART": "백색의 장방형 필름코팅정",
        "BAR_CODE": "8806469010038",
        "MATERIAL_NAME": "아세트아미노펜",
        "EE_DOC_ID": "DOC_20200612_000001",
        "UD_DOC_ID": "DOC_20200612_000002",
        "NB_DOC_ID": "DOC_20200612_000003",
        "INSERT_FILE": null,
        "STORAGE_METHOD": "기밀용기, 실온(1~30℃)보관",
        "VALID_TERM": "제조일로부터 36개월",
        "REEXAM_TARGET": null,
        "REEXAM_DATE": null,
        "PACK_UNIT": "100정/병",
        "EDI_CODE": "645901040",
        "DOC_TEXT": null,
        "PERMIT_KIND_NAME": "허가",
        "ENTP_NO": "19530001",
        "MAKE_MATERIAL_FLAG": null,
        "NEWDRUG_CLASS_NAME": null,
        "INDUTY_TYPE": "의약품",
        "CANCEL_DATE": null,
        "CANCEL_NAME": null,
        "CHANGE_DATE": "20230515",
        "NARCOTIC_KIND_CODE": null,
        "GBN_NAME": "완제의약품",
        "TOTAL_CONTENT": "1정(325mg) 중",
        "EE_DOC_DATA": "<DOC title=\"효능효과\"><SECTION title=\"\"><ARTICLE title=\"\">해열 및 감기에 의한 동통(통증)과 두통, 치통, ...</ARTICLE></SECTION></DOC>",
        "UD_DOC_DATA": "<DOC title=\"용법용량\"><SECTION title=\"\"><ARTICLE title=\"\">성인: 1회 1~2정씩, 1일 3~4회 (4~6시간 마다) 필요시 복용...</ARTICLE></SECTION></DOC>",
        "NB_DOC_DATA": "<DOC title=\"사용상의주의사항\"><SECTION title=\"1. 경고\"><ARTICLE title=\"\">매일 세잔 이상 정기적으로 술을 마시는 사람이 이 약...</ARTICLE></SECTION></DOC>",
        "PN_DOC_DATA": null,
        "MAIN_ITEM_INGR": "아세트아미노펜",
        "INGR_NAME": "아세트아미노펜"
      }
    ]
  }
}

=== 주요 응답 필드 설명 ===

| 필드명 | 설명 |
|--------|------|
| ITEM_SEQ | 품목기준코드 (고유 식별자) |
| ITEM_NAME | 제품명 |
| ENTP_NAME | 업체명 (제조/수입사) |
| ITEM_PERMIT_DATE | 허가일자 |
| ETC_OTC_CODE | 전문/일반 구분 |
| CHART | 성상 (약의 외형 설명) |
| BAR_CODE | 바코드 |
| MATERIAL_NAME | 원료성분 |
| STORAGE_METHOD | 저장방법 |
| VALID_TERM | 유효기간 |
| PACK_UNIT | 포장단위 |
| EDI_CODE | EDI 코드 |
| TOTAL_CONTENT | 총량 |
| EE_DOC_DATA | 효능효과 (XML 형식) |
| UD_DOC_DATA | 용법용량 (XML 형식) |
| NB_DOC_DATA | 주의사항 (XML 형식) |
| PN_DOC_DATA | 일반적주의 (XML 형식) |
| MAIN_ITEM_INGR | 주성분 |
| INGR_NAME | 첨가제 포함 전체 성분 |

*/

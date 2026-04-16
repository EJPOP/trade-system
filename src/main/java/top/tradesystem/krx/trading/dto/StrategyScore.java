package top.tradesystem.krx.trading.dto;

public record StrategyScore(
    String symbol,
    String name,
    double ofiScore,      // 실시간 수급 불균형
    double rsScore,       // 상대적 강도 (Relative Strength)
    double instScore,     // 기관 매집 점수
    double momentumScore, // 신고가 근접도
    double totalScore,    // 가중치 합산 총점
    double recommendedWeight // 켈리 공식에 따른 비중
) {}

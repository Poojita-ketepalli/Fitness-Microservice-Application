package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityAIService {
    private final GeminiService geminiService;

    public Recommendation generateRecommendation(Activity activity){
        String prompt = createPromptForActivity(activity);
        String aiResponse = geminiService.getAnswer(prompt);
        log.info("Response from AI:{}",aiResponse);
        return processAIResponse(activity, aiResponse);

    }

    private Recommendation processAIResponse(Activity activity,String aiResponse){
        try{
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(aiResponse);
            JsonNode textNode = rootNode.path("steps")
                    .get(1)
                    .path("content")
                    .get(0)
                    .path("text");
            String jsonContent = textNode.asText().replaceAll("```json\\n","")
                    .replaceAll("\\n```","")
                    .trim();
            //log.info("PARSED RESPONSE:{}",jsonContent);

            JsonNode analysisJson = mapper.readTree(jsonContent);
            JsonNode analysisNode = analysisJson.path("analysis");
            StringBuilder fullAnalysis = new StringBuilder();
            addAnalysisSection(fullAnalysis,analysisNode,"overall","Overall:");
            addAnalysisSection(fullAnalysis,analysisNode,"pace","Pace:");
            addAnalysisSection(fullAnalysis,analysisNode,"heartRate","Heart Rate:");
            addAnalysisSection(fullAnalysis,analysisNode,"CaloriesBurned","Calories Burned:");

            List<String> improvements = extractImprovements(analysisJson.path("improvements"));
            List<String> suggestions = extractSuggestions(analysisJson.path("suggestions"));
            List<String> safety = extractSafetyGuidelines(analysisJson.path("safety"));

            return Recommendation.builder()
                    .activityId(activity.getId())
                    .userId(activity.getUserId())
                    .activityType(activity.getType())
                    .recommendation(fullAnalysis.toString().trim())
                    .improvements(improvements)
                    .suggestions(suggestions)
                    .safety(safety)
                    .createdAt(LocalDateTime.now())
                    .build();

        }catch (Exception e){
            e.printStackTrace();
            return createDefaultRecommendation(activity);
        }
    }

    private Recommendation createDefaultRecommendation(Activity activity) {
        return Recommendation.builder()
                .activityId(activity.getId())
                .userId(activity.getUserId())
                .activityType(activity.getType())
                .recommendation("Unable to generate detailed analysis at this time. Please try again later.")
                .improvements(Collections.singletonList("Continue with your current routine"))
                .suggestions(Collections.singletonList("Consider consulting a fitness professional for personalized advice."))
                .safety(Collections.singletonList("Follow General safety guidelines."))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private List<String> extractSafetyGuidelines(JsonNode safety) {
        List<String> safetyList = new ArrayList<>();
        if(safety.isArray()){
            safety.forEach(guideline ->{
                safetyList.add(guideline.asText());
            });
        }
        return safetyList.isEmpty()? Collections.singletonList("Follow General safety guidelines"):
                safetyList;
    }

    private List<String> extractSuggestions(JsonNode suggestions) {
        List<String> suggestionsList = new ArrayList<>();
        if(suggestions.isArray()){
            suggestions.forEach(improvement ->{
                String area = improvement.path("workout").asText();
                String recommendation = improvement.path("description").asText();
                suggestionsList.add(String.format("Workout: %s, Description: %s", area, recommendation));
            });
        }
        return suggestionsList.isEmpty()? Collections.singletonList("No specific suggestions provided"):
                suggestionsList;
    }

    private List<String> extractImprovements(JsonNode improvements) {
        List<String> improvementList = new ArrayList<>();
        if(improvements.isArray()){
            improvements.forEach(improvement ->{
                String area = improvement.path("area").asText();
                String recommendation = improvement.path("recommendation").asText();
                improvementList.add(String.format("Area: %s, Recommendation: %s", area, recommendation));
            });
        }
        return improvementList.isEmpty()? Collections.singletonList("No specific improvements provided"):
                improvementList;
    }

    private void addAnalysisSection(StringBuilder fullAnalysis, JsonNode analysisNode, String key, String prefix) {
        if(!analysisNode.path(key).isMissingNode()){
            fullAnalysis.append(prefix).append(" ")
                    .append(analysisNode.path(key).asText()).append("\n\n");
        }
    }

    private String createPromptForActivity(Activity activity) {
        return String.format(""" 
                  Analyze this fitness activity and provide detailed recommendations in the following format
                  {
                      "analysis" : {
                          "overall": "Overall analysis here",
                          "pace": "Pace analysis here",
                          "heartRate": "Heart rate analysis here",
                          "CaloriesBurned": "Calories Burned here"
                      },
                      "improvements": [
                          {
                              "area": "Area name",
                              "recommendation": "Detailed Recommendation"
                          }
                      ],
                      "suggestions" : [
                          {
                              "workout": "Workout name",
                              "description": "Detailed workout description"
                          }
                      ],
                      "safety": [
                          "Safety point 1",
                          "Safety point 2"
                      ]
                  }
                
                  Analyze this activity:
                  Activity Type: %s
                  Duration: %d minutes
                  calories Burned: %d
                  Additional Metrics: %s
                  
                  provide detailed analysis focusing on performance, improvements, next workout suggestions, and safety guidelines
                  Ensure the response follows the EXACT JSON format shown above.             \s
                  """,
                activity.getType(),
                activity.getDuration(),
                activity.getCaloriesBurned(),
                activity.getAdditionalMetrics());
    }
}

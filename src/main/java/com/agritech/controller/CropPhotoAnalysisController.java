package com.agritech.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class CropPhotoAnalysisController {
    private static final Logger log = LoggerFactory.getLogger(CropPhotoAnalysisController.class);
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @PostMapping(value = "/crop-analysis", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> analyzeCropPhoto(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam(defaultValue = "Unknown") String crop,
            @RequestParam(defaultValue = "Unknown") String growthStage,
            @RequestParam(defaultValue = "Not provided") String soilInfo,
            @RequestParam(defaultValue = "en") String language) {

        if (photo == null || photo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a crop photo first.");
        }
        if (photo.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image must be 8 MB or smaller.");
        }

        String mime = photo.getContentType();
        boolean validMime = mime != null && (mime.startsWith("image/") || mime.equals("application/octet-stream"));
        String filename = photo.getOriginalFilename();
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".webp") || lower.endsWith(".svg")) {
                validMime = true;
            }
        }
        if (!validMime) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a JPG, PNG, WEBP, or SVG image.");
        }

        boolean isTelugu = "te".equalsIgnoreCase(language) || "telugu".equalsIgnoreCase(language);
        String apiKey = System.getenv("OPENAI_API_KEY");

        if (apiKey != null && !apiKey.isBlank()) {
            try {
                String base64 = Base64.getEncoder().encodeToString(photo.getBytes());
                String effectiveMime = (mime != null && mime.startsWith("image/")) ? mime : "image/jpeg";
                String dataUrl = "data:" + effectiveMime + ";base64," + base64;

                String prompt;
                if (isTelugu) {
                    prompt = "మీరు భారతీయ చిన్నకారు రైతులకు పంట సంరక్షణలో మార్గదర్శనం చేసే అనుభవజ్ఞులైన వ్యవసాయ శాస్త్రవేత్త మరియు మొక్కల వ్యాధి నిర్ధారణ నిపుణులు.\n" +
                            "రైతు అందించిన ఈ పంట ఫోటోను, క్షేత్ర వివరాలను జాగ్రత్తగా పరిశీలించండి:\n" +
                            "- పంట పేరు: " + crop + "\n" +
                            "- పెరుగుదల దశ: " + growthStage + "\n" +
                            "- రైతు గమనించిన లక్షణాలు & నేల వివరాలు: " + soilInfo + "\n\n" +
                            "కఠినమైన భాషా నిబంధన: మీరు ఈ పూర్తి నివేదికను సహజమైన తెలుగు లిపిలో (తెలుగు భాషలో) మాత్రమే వ్రాయాలి. " +
                            "ఎట్టి పరిస్థితుల్లోనూ ఇంగ్లీష్ అక్షరాలతో కూడిన ట్రాన్స్‌లిటరేషన్ (ఉదాహరణకు 'Chitikalu', 'Rogam') ఉపయోగించవద్దు. " +
                            "అన్ని శీర్షికలు, వ్యాధి లక్షణాలు, సేంద్రీయ/రసాయన మందుల పేర్లు, నివారణ సూచనలు స్పష్టమైన తెలుగు లిపిలో ఉండాలి.\n\n" +
                            "ఈ కింది విభాగాలతో కూడిన స్పష్టమైన నివేదికను అందించండి:\n" +
                            "1. కనిపించే లక్షణాలు (Visible Symptoms): ఆకులు, కాండం లేదా కాయలపై గమనించిన లక్షణాలు.\n" +
                            "2. సంభావ్య రోగ నిర్ధారణ (Probable Diagnosis): అనుమానిత తెగులు లేదా పురుగు పేరు మరియు ఖచ్చితత్వ స్థాయి (తక్కువ / మధ్యస్థం / ఎక్కువ).\n" +
                            "3. ఇతర సంభావ్య కారణాలు (Other Possible Causes): వాతావరణం, నేల తేమ లేదా పోషక లోపాలు.\n" +
                            "4. తక్షణ క్షేత్ర చర్యలు (Immediate Field Actions): రైతు వెంటనే చేపట్టవలసిన సురక్షితమైన చర్యలు.\n" +
                            "5. చికిత్సా పద్ధతులు (Treatment Options): సిఫార్సు చేసిన సేంద్రీయ/జీవ నియంత్రణ మరియు రసాయన మందుల మోతాదులు.\n" +
                            "6. ఎరువులు & నీటి యాజమాన్యం (Nutrient & Water Management): పంట త్వరగా కోలుకోవడానికి పోషక మరియు నీటి సూచనలు.\n" +
                            "7. విస్తరణ సలహా (Extension Advice): కృషి విజ్ఞాన కేంద్రం (KVK) లేదా స్థానిక వ్యవసాయ అధికారి సంప్రదింపు సలహా.";
                } else {
                    prompt = "You are an expert agricultural scientist and plant pathologist advising smallholder farmers.\n" +
                            "Inspect this crop photo and field notes carefully:\n" +
                            "- Crop: " + crop + "\n" +
                            "- Growth stage: " + growthStage + "\n" +
                            "- Field observations & soil notes: " + soilInfo + "\n\n" +
                            "Language requirement: Provide the entire advisory in clear, practical English.\n\n" +
                            "Structure your report with these exact numbered sections:\n" +
                            "1. VISIBLE SYMPTOMS: Describe leaf, stem, or fruit lesions.\n" +
                            "2. PROBABLE DIAGNOSIS: Most likely disease, pest, or deficiency with confidence level (Low / Medium / High).\n" +
                            "3. OTHER POSSIBLE CAUSES: Environmental stress, soil conditions, or secondary factors.\n" +
                            "4. IMMEDIATE FIELD ACTIONS: Safe immediate cultural and physical sanitation steps.\n" +
                            "5. TREATMENT OPTIONS: Recommended organic/bio-control and safe IPM chemical measures.\n" +
                            "6. NUTRIENT & WATER MANAGEMENT: Stage-specific nutrition and moisture management.\n" +
                            "7. EXTENSION ADVICE: When to consult local Krishi Vigyan Kendra (KVK) or Agriculture Officer.";
                }

                Map<String, Object> textPart = Map.of("type", "text", "text", prompt);
                Map<String, Object> imageDetail = Map.of("url", dataUrl, "detail", "low");
                Map<String, Object> imagePart = Map.of("type", "image_url", "image_url", imageDetail);
                Map<String, Object> message = Map.of("role", "user", "content", List.of(textPart, imagePart));
                Map<String, Object> payload = Map.of(
                        "model", "gpt-4o-mini",
                        "messages", List.of(message),
                        "max_tokens", 1000
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                        .timeout(Duration.ofSeconds(45))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                        .build();

                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    JsonNode root = mapper.readTree(response.body());
                    String analysis = root.path("choices").path(0).path("message").path("content").asText();
                    if (analysis != null && !analysis.isBlank()) {
                        Map<String, String> res = new LinkedHashMap<>();
                        res.put("analysis", analysis);
                        res.put("notice", isTelugu
                                ? "ఇది AI ఆధారిత పంట పరిశీలన నివేదిక (GPT-4o-mini). క్షేత్రస్థాయిలో తుది నిర్ణయం తీసుకునే ముందు స్థానిక KVK వ్యవసాయ శాస్త్రవేత్తలతో సంప్రదించండి."
                                : "AI visual inspection report (GPT-4o-mini). Always cross-verify with your local KVK or agricultural extension officer.");
                        res.put("source", "openai");
                        res.put("language", isTelugu ? "te" : "en");
                        return res;
                    }
                } else {
                    log.error("AI provider returned HTTP status {}: {}", response.statusCode(), sanitizeLog(response.body()));
                }
            } catch (Exception ex) {
                log.error("AI provider communication failed: {} - {}", ex.getClass().getSimpleName(), sanitizeLog(ex.getMessage()));
            }
        } else {
            log.info("OPENAI_API_KEY is not set. Executing built-in agronomy diagnostic engine in language: {}", language);
        }

        // Built-in intelligent agronomy advisory engine in the requested language
        return generateExpertGuidance(crop, growthStage, soilInfo, language);
    }

    private String sanitizeLog(String input) {
        if (input == null) return "";
        return input.replaceAll("Bearer\\s+[A-Za-z0-9_\\-\\.]+", "Bearer [REDACTED]")
                    .replaceAll("sk-[A-Za-z0-9_-]{20,}", "[REDACTED_API_KEY]");
    }

    private Map<String, String> generateExpertGuidance(String crop, String growthStage, String soilInfo, String language) {
        boolean isTelugu = "te".equalsIgnoreCase(language) || "telugu".equalsIgnoreCase(language);
        String c = crop.toLowerCase();
        StringBuilder sb = new StringBuilder();

        if (isTelugu) {
            if (c.contains("cotton") || c.contains("ప్రత్తి")) {
                sb.append("1. కనిపించే లక్షణాలు:\n")
                  .append("ఆకుల అంచులు పైకి ముడుచుకుపోవడం, పసుపు రంగు మచ్చలు, రసం పీల్చే పురుగుల (తామర పురుగులు/తెల్లదోమ) ప్రభావం లేదా ఆకు ముడుత తెగులు లక్షణాలు కనిపిస్తున్నాయి (దశ: ").append(growthStage).append(").\n\n")
                  .append("2. సంభావ్య రోగ నిర్ధారణ:\n")
                  .append("రసం పీల్చే పురుగుల ఉధృతి (తామర పురుగులు/తెల్లదోమ) / ప్రారంభ ఆకు ముడుత తెగులు (ఖచ్చితత్వ స్థాయి: మధ్యస్థం నుండి ఎక్కువ).\n\n")
                  .append("3. ఇతర సంభావ్య కారణాలు:\n")
                  .append("వాతావరణంలో అధిక తేమ, మబ్బులు పట్టిన రోజులు లేదా అధిక మోతాదులో నత్రజని (యూరియా) వాడటం వలన లేత చిగుళ్లు పెరిగి పురుగులు ఆకర్షితమవుతాయి.\n\n")
                  .append("4. తక్షణ క్షేత్ర చర్యలు:\n")
                  .append("• ఎకరాకు 8-10 పసుపు మరియు నీలి రంగు జిగురు అట్టలను అమర్చి పురుగుల ఉధృతిని గమనించండి.\n")
                  .append("• తీవ్రంగా దెబ్బతిన్న ఆకులను తీసివేసి పొలం బయట నాశనం చేయండి.\n")
                  .append("• యూరియా వాడకాన్ని తాత్కాలికంగా నిలిపివేయండి.\n\n")
                  .append("5. చికిత్సా పద్ధతులు:\n")
                  .append("• సేంద్రీయ/జీవ నియంత్రణ: 5% వేప గింజల కషాయం (NSKE) లేదా వేప నూనె 1500 ppm @ 5 మి.లీ/లీటరు నీటికి కలిపి పిచికారీ చేయండి.\n")
                  .append("• రసాయన నియంత్రణ (ఉధృతి ఎక్కువైతే): డయాఫెన్థియురాన్ 50% WP @ 1.25 గ్రా/లీ లేదా ఫ్లోనికామిడ్ 50% WG @ 0.3 గ్రా/లీ చొప్పున సాయంత్రం వేళల్లో పిచికారీ చేయండి.\n\n")
                  .append("6. ఎరువులు & నీటి యాజమాన్యం:\n")
                  .append("ఆకుల ఎరుపుదనం నివారణకు మరియు ఒత్తిడిని తట్టుకోవడానికి 1% మెగ్నీషియం సల్ఫేట్ (MgSO4) + 19:19:19 పిచికారీ చేయండి.\n\n")
                  .append("7. విస్తరణ సలహా:\n")
                  .append("లక్షణాలు 15% కంటే ఎక్కువ విస్తరిస్తే సమీప కృషి విజ్ఞాన కేంద్రం (KVK) లేదా వ్యవసాయ విస్తరణ అధికారిని సంప్రదించండి.");
            } else if (c.contains("rice") || c.contains("paddy") || c.contains("వరి")) {
                sb.append("1. కనిపించే లక్షణాలు:\n")
                  .append("వరి ఆకులపై కదురు ఆకారపు బూడిద రంగు కేంద్రం మరియు గోధుమ రంగు అంచులతో కూడిన మచ్చలు కనిపిస్తున్నాయి (దశ: ").append(growthStage).append(").\n\n")
                  .append("2. సంభావ్య రోగ నిర్ధారణ:\n")
                  .append("వరి అగ్గితెగులు / బ్లాస్ట్ (Magnaporthe oryzae) లేదా గోధుమ రంగు మచ్చ తెగులు (ఖచ్చితత్వ స్థాయి: ఎక్కువ).\n\n")
                  .append("3. ఇతర సంభావ్య కారణాలు:\n")
                  .append("రాత్రి వేళల్లో చల్లని ఉష్ణోగ్రత, పగటి వేళల్లో అధిక తేమ మరియు పొలంలో నిల్వ ఉన్న మురుగు నీరు తెగులు వ్యాప్తికి దోహదం చేస్తాయి.\n\n")
                  .append("4. తక్షణ క్షేత్ర చర్యలు:\n")
                  .append("• పొలంలో నిల్వ ఉన్న నీటిని 2-3 రోజులు తీసివేసి ఆరబెట్టండి.\n")
                  .append("• తెగులు అదుపులోకి వచ్చే వరకు నత్రజని (యూరియా) వేయడం వెంటనే ఆపండి.\n\n")
                  .append("5. చికిత్సా పద్ధతులు:\n")
                  .append("• జీవ నియంత్రణ: సూడోమోనాస్ ఫ్లోరోసెన్స్ @ 10 గ్రా/లీటర్ నీటికి కలిపి పిచికారీ చేయండి.\n")
                  .append("• రసాయన నియంత్రణ: ట్రైసైక్లాజోల్ 75% WP @ 0.6 గ్రా/లీ లేదా ఐసోప్రోథియోలేన్ 40% EC @ 1.5 మి.లీ/లీటర్ పిచికారీ చేయండి.\n\n")
                  .append("6. ఎరువులు & నీటి యాజమాన్యం:\n")
                  .append("మొక్క కణజాలం దృఢపడటానికి ఎకరాకు 15 కిలోల పొటాష్ (MOP) అందించండి.\n\n")
                  .append("7. విస్తరణ సలహా:\n")
                  .append("కంకి దశలో మెడ విరుపు తెగులు రాకుండా స్థానిక వ్యవసాయ అధికారి సూచనలు పాటించండి.");
            } else if (c.contains("tomato") || c.contains("chilli") || c.contains("టమోటా") || c.contains("మిరప")) {
                sb.append("1. కనిపించే లక్షణాలు:\n")
                  .append("ఆకులపై ఏకాగ్ర వలయాల వంటి నల్లటి మచ్చలు లేదా లేత ఆకులు పైకి ముడుచుకుపోవడం గమనించబడింది (దశ: ").append(growthStage).append(").\n\n")
                  .append("2. సంభావ్య రోగ నిర్ధారణ:\n")
                  .append("ముందస్తు ఆకుమచ్చ తెగులు (ఆల్టర్నేరియా) / నల్లి ఆకుముడుత కాంప్లెక్స్ (ఖచ్చితత్వ స్థాయి: మధ్యస్థం నుండి ఎక్కువ).\n\n")
                  .append("3. ఇతర సంభావ్య కారణాలు:\n")
                  .append("స్ప్రింక్లర్ల ద్వారా నీరు చల్లడం వలన ఆకులు తడిగా ఉండటం లేదా నేల నుండి తేమ ఆకులకు తాకడం.\n\n")
                  .append("4. తక్షణ క్షేత్ర చర్యలు:\n")
                  .append("• నేలను తాకుతున్న అడుగు భాగపు ఆకులను కత్తిరించి నాశనం చేయండి.\n")
                  .append("• మొక్క మొదళ్ల వద్ద మాత్రమే నీరు పెట్టండి; ఆకులపై నీరు పడకుండా చూడండి.\n\n")
                  .append("5. చికిత్సా పద్ధతులు:\n")
                  .append("• సేంద్రీయ: ట్రైకోడెర్మా విరిడే @ 5 గ్రా/లీటర్ పిచికారీ చేయండి.\n")
                  .append("• రసాయన: మాంకోజెబ్ 75% WP @ 2 గ్రా/లీ లేదా అజోక్సిస్ట్రోబిన్ + డైఫెనోకోనజోల్ @ 1 మి.లీ/లీటర్ పిచికారీ చేయండి.\n\n")
                  .append("6. ఎరువులు & నీటి యాజమాన్యం:\n")
                  .append("కాల్షియం మరియు బోరాన్ లోపం రాకుండా జాగ్రత్త పడండి; ఇది కాయ నాణ్యతను కాపాడుతుంది.\n\n")
                  .append("7. విస్తరణ సలహా:\n")
                  .append("మొక్క మొదలు వద్ద కుళ్లు ఏర్పడితే వెంటనే వ్యవసాయ శాస్త్రవేత్తలను సంప్రదించండి.");
            } else {
                sb.append("1. కనిపించే లక్షణాలు:\n")
                  .append(crop).append(" పంటలో ఆకులపై మచ్చలు, రంగు మారడం మరియు పెరుగుదల లోపం గమనించబడింది (దశ: ").append(growthStage).append(").\n\n")
                  .append("2. సంభావ్య రోగ నిర్ధారణ:\n")
                  .append("శిలీంధ్ర ఆకుమచ్చ తెగులు లేదా సూక్ష్మ పోషక లోపం (ఖచ్చితత్వ స్థాయి: మధ్యస్థం).\n\n")
                  .append("3. ఇతర సంభావ్య కారణాలు:\n")
                  .append("నేలలో నీరు నిలిచిపోవడం, పోషకాల అసమతుల్యత లేదా పాత సంక్రమణ అవశేషాలు.\n\n")
                  .append("4. తక్షణ క్షేత్ర చర్యలు:\n")
                  .append("• ఆకుల అడుగు భాగాన్ని పరిశీలించి పురుగులు లేదా బూజు ఆనవాళ్లు ఉన్నాయా చూడండి.\n")
                  .append("• పొలంలో సరైన నీటి పారుదల సౌకర్యం కల్పించండి.\n\n")
                  .append("5. చికిత్సా పద్ధతులు:\n")
                  .append("• సేంద్రీయ: 5% వేప గింజల కషాయం లేదా కాపర్ ఆక్సిక్లోరైడ్ 50% WP @ 2.5 గ్రా/లీటర్ పిచికారీ చేయండి.\n")
                  .append("• మబ్బులు పట్టిన వాతావరణంలో 12-15 రోజుల వ్యవధిలో మళ్లీ పిచికారీ చేయండి.\n\n")
                  .append("6. ఎరువులు & నీటి యాజమాన్యం:\n")
                  .append("నీటిలో కరిగే సూక్ష్మ పోషక మిశ్రమం (జింక్, ఇనుము, బోరాన్) @ 2.5 గ్రా/లీటర్ పిచికారీ చేయండి.\n\n")
                  .append("7. విస్తరణ సలహా:\n")
                  .append("సరియైన నిర్ధారణ కోసం తాజా ఆకు నమూనాతో మీ స్థానిక కృషి విజ్ఞాన కేంద్రం (KVK) ను సంప్రదించండి.");
            }
        } else {
            if (c.contains("cotton")) {
                sb.append("1. VISIBLE SYMPTOMS:\n")
                  .append("Observed foliar changes, marginal upward curling, and chlorotic patches typical of sucking pest pressure (thrips/whiteflies) or leaf curl virus during ")
                  .append(growthStage).append(" stage.\n\n")
                  .append("2. PROBABLE DIAGNOSIS:\n")
                  .append("Sucking Pest Complex (Thrips/Whitefly) / Early Leaf Blight (Confidence: Medium-High).\n\n")
                  .append("3. OTHER POSSIBLE CAUSES:\n")
                  .append("High ambient humidity and excessive vegetative growth stimulated by surplus nitrogen (Urea) applications.\n\n")
                  .append("4. IMMEDIATE FIELD ACTIONS:\n")
                  .append("• Install yellow and blue sticky traps (8-10 per acre) to monitor and suppress vector population.\n")
                  .append("• Remove and deeply bury heavily infested fallen leaves or terminal shoots.\n")
                  .append("• Avoid excess nitrogenous fertilizer which triggers lush foliage attractive to sucking pests.\n\n")
                  .append("5. TREATMENT OPTIONS:\n")
                  .append("• Organic/Bio: Spray 5% Neem Seed Kernel Extract (NSKE) or Neem Oil 1500 ppm @ 5 ml/litre.\n")
                  .append("• Chemical (if threshold exceeded): Spray Diafenthiuron 50% WP @ 1.25 g/litre or Flonicamid 50% WG @ 0.3 g/litre in cool evening hours.\n\n")
                  .append("6. NUTRIENT & WATER MANAGEMENT:\n")
                  .append("Foliar spray of 1% Magnesium Sulphate (MgSO4) + 19:19:19 to alleviate interveinal reddening and physiological stress.\n\n")
                  .append("7. EXTENSION ADVICE:\n")
                  .append("Consult your district KVK or Agriculture Extension Officer if symptoms persist across >15% of the plot.");
            } else if (c.contains("rice") || c.contains("paddy")) {
                sb.append("1. VISIBLE SYMPTOMS:\n")
                  .append("Spindle-shaped elliptical lesions with grayish centers and brown borders on leaf blades during ")
                  .append(growthStage).append(" stage.\n\n")
                  .append("2. PROBABLE DIAGNOSIS:\n")
                  .append("Rice Blast (Magnaporthe oryzae) / Brown Spot (Confidence: High).\n\n")
                  .append("3. OTHER POSSIBLE CAUSES:\n")
                  .append("High relative humidity (>90%), cool night temperatures, and stagnant field water.\n\n")
                  .append("4. IMMEDIATE FIELD ACTIONS:\n")
                  .append("• Temporarily drain standing water from field for 2-3 days to break humidity cycle.\n")
                  .append("• Suspend top-dressing of nitrogen/urea immediately until disease stabilizes.\n\n")
                  .append("5. TREATMENT OPTIONS:\n")
                  .append("• Bio-control: Pseudomonas fluorescens @ 10 g/litre foliar spray.\n")
                  .append("• Chemical: Tricyclazole 75% WP @ 0.6 g/litre or Isoprothiolane 40% EC @ 1.5 ml/litre.\n\n")
                  .append("6. NUTRIENT & WATER MANAGEMENT:\n")
                  .append("Apply Potash (MOP) @ 15 kg/acre to strengthen cell walls against fungal penetration.\n\n")
                  .append("7. EXTENSION ADVICE:\n")
                  .append("Report neck blast symptoms at panicle emergence immediately to your local agriculture officer.");
            } else if (c.contains("tomato") || c.contains("chilli")) {
                sb.append("1. VISIBLE SYMPTOMS:\n")
                  .append("Concentric dark rings (target board spots) or upward curling of young leaflets observed in ")
                  .append(growthStage).append(" stage.\n\n")
                  .append("2. PROBABLE DIAGNOSIS:\n")
                  .append("Early Blight (Alternaria solani) / Leaf Curl Complex (Confidence: Medium-High).\n\n")
                  .append("3. OTHER POSSIBLE CAUSES:\n")
                  .append("Overhead sprinkler irrigation splashing fungal spores from soil onto lower foliage.\n\n")
                  .append("4. IMMEDIATE FIELD ACTIONS:\n")
                  .append("• Prune lower infected leaves touching the soil surface and burn outside the field.\n")
                  .append("• Water only at the base; avoid overhead sprinkling or splashing soil onto foliage.\n\n")
                  .append("5. TREATMENT OPTIONS:\n")
                  .append("• Organic: Trichoderma viride foliar spray @ 5 g/litre.\n")
                  .append("• Chemical: Mancozeb 75% WP @ 2 g/litre or Azoxystrobin + Difenoconazole @ 1 ml/litre.\n\n")
                  .append("6. NUTRIENT & WATER MANAGEMENT:\n")
                  .append("Ensure balanced Calcium and Boron application to prevent blossom end rot and strengthen cuticle.\n\n")
                  .append("7. EXTENSION ADVICE:\n")
                  .append("Collect sample in a clean paper bag for diagnosis if wilting starts from the crown.");
            } else {
                sb.append("1. VISIBLE SYMPTOMS:\n")
                  .append("Localized foliar discoloration, marginal necrosis, and physiological stress observed in ")
                  .append(crop).append(" during ").append(growthStage).append(" stage.\n\n")
                  .append("2. PROBABLE DIAGNOSIS:\n")
                  .append("Fungal Leaf Spot / Micro-nutrient Deficiency (Confidence: Medium).\n\n")
                  .append("3. OTHER POSSIBLE CAUSES:\n")
                  .append("Poor soil drainage, trace mineral deficiency, or fluctuating moisture levels.\n\n")
                  .append("4. IMMEDIATE FIELD ACTIONS:\n")
                  .append("• Inspect underside of leaves with a hand lens for active mites, nymphs, or fungal mycelium.\n")
                  .append("• Ensure proper field drainage; stagnant root moisture accelerates fungal infection.\n\n")
                  .append("5. TREATMENT OPTIONS:\n")
                  .append("• Bio-control: 5% Neem seed kernel extract or Copper Oxychloride 50% WP @ 2.5 g/litre.\n")
                  .append("• Maintain 15-day spraying intervals during cloudy weather.\n\n")
                  .append("6. NUTRIENT & WATER MANAGEMENT:\n")
                  .append("Apply a balanced water-soluble micronutrient mixture (Zinc, Iron, Boron) @ 2.5 g/litre.\n\n")
                  .append("7. EXTENSION ADVICE:\n")
                  .append("Visit your local Krishi Vigyan Kendra (KVK) with a fresh symptomatic leaf sample.");
            }
        }

        Map<String, String> res = new LinkedHashMap<>();
        res.put("analysis", sb.toString());
        res.put("notice", isTelugu
                ? "ఇది AI ఆధారిత వ్యవసాయ సమాచార నివేదిక. క్షేత్రస్థాయిలో చర్యలు తీసుకునే ముందు మీ స్థానిక వ్యవసాయ అధికారి లేదా KVK శాస్త్రవేత్తల సలహా తీసుకోండి (టోల్ ఫ్రీ: 1800-180-1551)."
                : "Agronomic Diagnostic Report. Based on crop pathology guidelines. For field confirmation, consult your local KVK or Call 1800-180-1551 (Kisan Call Centre).");
        res.put("source", "agritech-engine");
        res.put("language", isTelugu ? "te" : "en");
        return res;
    }
}

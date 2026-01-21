package ilp.tutorials.pizzadronz.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.inf.ilp.data.LngLat;
import uk.ac.ed.inf.ilp.constant.OrderValidationCode;
import uk.ac.ed.inf.ilp.constant.SystemConstants;
import uk.ac.ed.inf.ilp.data.*;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class PizzaDronzController {

    private static final String BASE_REST_URL = "https://ilp-rest-2024.azurewebsites.net";
    private final Gson gson = new Gson();

    // receive uuid from system
    @GetMapping("/uuid")
    public String getUUID() {
        return "s2190304";
    }

    // calculate distance between two positions
    @PostMapping("/distanceTo")
    public ResponseEntity<?> distanceTo(@RequestBody Map<String, Object> request) {
        try {
            LngLat position1 = parseLngLat(request.get("position1"));
            LngLat position2 = parseLngLat(request.get("position2"));

            validateCoordinates(position1);
            validateCoordinates(position2);

            double distance = calculateDistance(position1, position2);
            return ResponseEntity.ok(distance);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // check if two positions within a certain proximity
    @PostMapping("/isCloseTo")
    public ResponseEntity<?> isCloseTo(@RequestBody Map<String, Object> request) {
        try {
            LngLat position1 = parseLngLat(request.get("position1"));
            LngLat position2 = parseLngLat(request.get("position2"));

            validateCoordinates(position1);
            validateCoordinates(position2);

            double distance = calculateDistance(position1, position2);
            return ResponseEntity.ok(distance < 0.00015);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // calculate next position of drone based on starting position + angle
    @PostMapping("/nextPosition")
    public ResponseEntity<?> nextPosition(@RequestBody Map<String, Object> request) {
        try {
            Map<String, Double> startMap = castToMap(request.get("start"));
            Double angle = castToDouble(request.get("angle"));

            if (startMap == null || angle == null) {
                throw new IllegalArgumentException("Invalid or missing input for start or angle.");
            }

            LngLat start = new LngLat(startMap.get("lng"), startMap.get("lat"));
            validateCoordinates(start);

            LngLat nextPos = calculateNextPosition(start, angle);
            return ResponseEntity.ok(nextPos);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // check if a given position is within specified region
    @PostMapping("/isInRegion")
    public ResponseEntity<?> isInRegion(@RequestBody Map<String, Object> request) {
        try {
            LngLat position = parseLngLat(request.get("position"));
            validateCoordinates(position);

            List<LngLat> vertices = extractVertices(castToList(request.get("region"), "vertices"));
            if (vertices.size() < 3) {
                throw new IllegalArgumentException("Region must have at least 3 vertices.");
            }

            for (LngLat vertex : vertices) {
                validateCoordinates(vertex);
            }

            boolean isInRegion = isPointInPolygon(position, vertices);
            return ResponseEntity.ok(isInRegion);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // validate pizza order
    @PostMapping("/validateOrder")
    public ResponseEntity<Order> validateOrder(@RequestBody Order order) {
        OrderValidationCode code;
        try {
            code = validateOrderLogic(order);
        } catch (IllegalArgumentException e) {
            code = OrderValidationCode.PIZZA_NOT_DEFINED;
        }
        return ResponseEntity.ok(withValidationResult(order, code));
    }



    // work out drone's delivery path for given order
    @PostMapping("/calcDeliveryPath")
    public ResponseEntity<?> calcDeliveryPath(@RequestBody Order order) {
        OrderValidationCode code = validateOrderLogic(order);

        if (code != OrderValidationCode.NO_ERROR) {
            return ResponseEntity.ok(withValidationResult(order, code));
        }

        List<LngLat> path = calculatePath(order);
        return ResponseEntity.ok(path);
    }


    // work out delivery path then return as GeoJSON object
    @PostMapping("/calcDeliveryPathAsGeoJson")
    public ResponseEntity<?> calcDeliveryPathAsGeoJson(@RequestBody Order order) {
        try {
            OrderValidationCode validationCode = validateOrderLogic(order);
            if (validationCode != OrderValidationCode.NO_ERROR) {
                throw new IllegalArgumentException("Invalid order: " + validationCode.name());
            }

            List<LngLat> path = calculatePath(order);
            String geoJson = convertPathToGeoJson(path);
            return ResponseEntity.ok(geoJson);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // utility methods
    private boolean closeTo(LngLat a, LngLat b) {
        double distance = Math.sqrt(Math.pow(a.lng() - b.lng(), 2) + Math.pow(a.lat() - b.lat(), 2));
        return distance < SystemConstants.DRONE_MOVE_DISTANCE;
    }

    public double calculateDistance(LngLat pos1, LngLat pos2) {
        double dLng = pos1.lng() - pos2.lng();
        double dLat = pos1.lat() - pos2.lat();
        return Math.sqrt(dLng * dLng + dLat * dLat);
    }

    private LngLat calculateNextPosition(LngLat start, double angle) {
        double distance = 0.00015; // Move distance
        double radians = Math.toRadians(angle);
        double newLng = start.lng() + distance * Math.cos(radians);
        double newLat = start.lat() + distance * Math.sin(radians);
        return new LngLat(newLng, newLat);
    }

    private boolean isPointInPolygon(LngLat point, List<LngLat> vertices) {
        int n = vertices.size();
        boolean inside = false;

        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = vertices.get(i).lng(), yi = vertices.get(i).lat();
            double xj = vertices.get(j).lng(), yj = vertices.get(j).lat();

            boolean intersect = ((yi > point.lat()) != (yj > point.lat())) &&
                    (point.lng() < (xj - xi) * (point.lat() - yi) / (yj - yi) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }

    public void validateCoordinates(LngLat coordinates) {
        if (coordinates.lng() < -180 || coordinates.lng() > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180.");
        }
        if (coordinates.lat() < -90 || coordinates.lat() > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90.");
        }
    }

    // utility method to parse a position object
    private LngLat parseLngLat(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            Object lng = map.get("lng");
            Object lat = map.get("lat");
            if (lng instanceof Number && lat instanceof Number) {
                return new LngLat(((Number) lng).doubleValue(), ((Number) lat).doubleValue());
            } else {
                throw new IllegalArgumentException("Coordinates must be numeric: lng=" + lng + ", lat=" + lat);
            }
        }
        throw new IllegalArgumentException("Invalid coordinate object: " + obj);
    }

    private List<LngLat> extractVertices(List<Map<String, Double>> vertexData) {
        List<LngLat> vertices = new ArrayList<>();
        for (Map<String, Double> data : vertexData) {
            vertices.add(new LngLat(data.get("lng"), data.get("lat")));
        }
        return vertices;
    }

    private Map<String, Double> castToMap(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            return (Map<String, Double>) map;
        }
        return null;
    }

    private Double castToDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        return null;
    }

    private <T> List<Map<String, T>> castToList(Object obj, String key) {
        if (obj instanceof Map<?, ?> map && map.get(key) instanceof List<?>) {
            return (List<Map<String, T>>) map.get(key);
        }
        return null;
    }

    // validate logic for the order to make sure it meets requirements
    private OrderValidationCode validateOrderLogic(Order order) {
        if (order == null || order.getPizzasInOrder() == null || order.getPizzasInOrder().length == 0) {
            return OrderValidationCode.EMPTY_ORDER;
        }

        if (order.getPizzasInOrder().length > SystemConstants.MAX_PIZZAS_PER_ORDER) {
            return OrderValidationCode.MAX_PIZZA_COUNT_EXCEEDED;
        }

        int calculatedTotal = Arrays.stream(order.getPizzasInOrder()).mapToInt(Pizza::priceInPence).sum();
        if (calculatedTotal + SystemConstants.ORDER_CHARGE_IN_PENCE != order.getPriceTotalInPence()) {
            return OrderValidationCode.TOTAL_INCORRECT;
        }

        // Validate credit card information directly
        CreditCardInformation cc = order.getCreditCardInformation();
        if (cc == null || cc.getCreditCardNumber() == null || !cc.getCreditCardNumber().matches("\\d{16}")) {
            return OrderValidationCode.CARD_NUMBER_INVALID;
        }
        if (cc.getCreditCardExpiry() == null || !isValidExpiryDate(cc.getCreditCardExpiry())) {
            return OrderValidationCode.EXPIRY_DATE_INVALID;
        }
        if (cc.getCvv() == null || !cc.getCvv().matches("\\d{3}")) {
            return OrderValidationCode.CVV_INVALID;
        }


        if (!validateRestaurants(order)) {
            return OrderValidationCode.PIZZA_FROM_MULTIPLE_RESTAURANTS;
        }

        return OrderValidationCode.NO_ERROR;
    }

    //validating expiry date
    private boolean isValidExpiryDate(String expiryDate) {

        if (!expiryDate.matches("\\d{2}/\\d{2}")) {
            return false;
        }

        try {

            String[] parts = expiryDate.split("/");
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]);


            if (month < 1 || month > 12) {
                return false;
            }


            java.time.YearMonth currentYearMonth = java.time.YearMonth.now();
            int currentYear = currentYearMonth.getYear() % 100;
            int currentMonth = currentYearMonth.getMonthValue();


            java.time.YearMonth expiryYearMonth = java.time.YearMonth.of(2000 + year, month);


            java.time.YearMonth maxExpiryDate = currentYearMonth.plusYears(5);
            return !expiryYearMonth.isBefore(currentYearMonth) && !expiryYearMonth.isAfter(maxExpiryDate);
        } catch (Exception e) {
            return false;
        }
    }

// validating credit card
    private boolean isValidCreditCard(CreditCardInformation creditCardInfo) {
        if (creditCardInfo == null) return false;

        String cardNumber = creditCardInfo.getCreditCardNumber();
        String expiryDate = creditCardInfo.getCreditCardExpiry();
        String cvv = creditCardInfo.getCvv();


        if (cardNumber == null || !cardNumber.matches("\\d{16}")) {
            return false;
        }


        if (expiryDate == null || !isValidExpiryDate(expiryDate)) {
            return false;
        }


        if (cvv == null || !cvv.matches("\\d{3}")) {
            return false;
        }

        return true;
    }


    // checking pizzas are valid
    private boolean validateRestaurants(Order order) {

        Restaurant[] restaurants = fetchAndParse("/restaurants", Restaurant[].class);


        Map<String, String> pizzaToRestaurantMap = Arrays.stream(restaurants)
                .flatMap(restaurant -> Arrays.stream(restaurant.menu())
                        .map(pizza -> Map.entry(pizza.name(), restaurant.name())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));


        Set<String> restaurantNames = Arrays.stream(order.getPizzasInOrder())
                .map(pizza -> {
                    String restaurantName = pizzaToRestaurantMap.get(pizza.name());
                    if (restaurantName == null) {
                        throw new IllegalArgumentException("Invalid pizza name: " + pizza.name());
                    }
                    return restaurantName;
                })
                .collect(Collectors.toSet());


        return restaurantNames.size() == 1;
    }

    // calculate delivery path for an order
    public List<LngLat> calculatePath(Order order) {
        LngLat start = getRestaurantLocation(order);
        LngLat destination = new LngLat(SystemConstants.APPLETON_LNG, SystemConstants.APPLETON_LAT);

        List<NamedRegion> noFlyZones = getNoFlyZones();
        return calculateDirectPath(start, destination, noFlyZones);
    }

    // find an alternative path if the drone enters a no-fly zone
    private LngLat findAlternativePath(LngLat current, LngLat target, List<NamedRegion> noFlyZones) {
        double moveDistance = SystemConstants.DRONE_MOVE_DISTANCE;

        for (int angleStep = 0; angleStep <= 360; angleStep += 15) {
            double adjustedAngle = Math.toRadians(angleStep);
            double angleToTarget = Math.atan2(target.lat() - current.lat(), target.lng() - current.lng());


            double newAngle = angleToTarget + adjustedAngle;
            double nextLng = current.lng() + moveDistance * Math.cos(newAngle);
            double nextLat = current.lat() + moveDistance * Math.sin(newAngle);

            LngLat candidateStep = new LngLat(nextLng, nextLat);


            if (!isInNoFlyZone(candidateStep, noFlyZones)) {
                return candidateStep;
            }
        }

        return null;
    }

    // extract restaurant prefix from a pizza name
    private String extractPrefixFromPizzaName(String pizzaName) {
        if (pizzaName.contains(":")) {
            return pizzaName.split(":")[0].trim();
        }
        throw new IllegalArgumentException("Invalid pizza name: " + pizzaName);
    }

    // retrieve the location of the restaurant for the given order
    private LngLat getRestaurantLocation(Order order) {
        String restaurantPrefix = getRestaurantPrefix(order);
        Restaurant[] restaurants = fetchAndParse("/restaurants", Restaurant[].class);


        return Arrays.stream(restaurants)
                .filter(restaurant -> Arrays.stream(restaurant.menu())
                        .anyMatch(pizza -> pizza.name().startsWith(restaurantPrefix + ":")))
                .map(Restaurant::location)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found for prefix: " + restaurantPrefix));
    }

    // fetches and parses the REST API response into the desired type
    public <T> T fetchAndParse(String endpoint, Class<T> responseType) {
        String response = fetchFromRestService(endpoint);
        return gson.fromJson(response, responseType);
    }

    // extracts the prefix for the restaurant from the order
    private String getRestaurantPrefix(Order order) {
        return Arrays.stream(order.getPizzasInOrder())
                .findFirst()
                .map(pizza -> extractPrefixFromPizzaName(pizza.name()))
                .orElseThrow(() -> new IllegalArgumentException("Order contains no pizzas."));
    }

    // fetch no-fly zones from the REST service
    private List<NamedRegion> getNoFlyZones() {
        return Arrays.asList(fetchAndParse("/noFlyZones", NamedRegion[].class));
    }

    // convert a path of LngLat points to GeoJSON format
    private String convertPathToGeoJson(List<LngLat> path) {
        Map<String, Object> geoJson = new HashMap<>();
        geoJson.put("type", "LineString");
        geoJson.put("coordinates", path.stream()
                .map(point -> Arrays.asList(point.lng(), point.lat()))
                .collect(Collectors.toList()));
        return gson.toJson(geoJson);
    }

    // fetch data from the REST service
    private String fetchFromRestService(String endpoint) {
        try {
            java.net.URL url = new java.net.URL(BASE_REST_URL + endpoint);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            try (java.io.InputStreamReader reader = new java.io.InputStreamReader(conn.getInputStream())) {
                return new java.util.Scanner(reader).useDelimiter("\\A").next();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to fetch data from REST service: " + endpoint, e);
        }
    }

    // fetch the central area boundary as LngLat points
    private List<LngLat> getCentralAreaBoundary() {
        Map<String, Object> centralArea = fetchAndParse("/centralArea", Map.class);


        if (!centralArea.containsKey("vertices") || centralArea.get("vertices") == null) {
            throw new IllegalArgumentException("Central Area response is missing 'vertices'.");
        }

        List<Map<String, Double>> vertices = (List<Map<String, Double>>) centralArea.get("vertices");
        List<LngLat> boundaryPoints = new ArrayList<>();


        for (Map<String, Double> vertex : vertices) {
            double lng = vertex.get("lng");
            double lat = vertex.get("lat");
            boundaryPoints.add(new LngLat(lng, lat));
        }

        return boundaryPoints;
    }

    // check if a point is inside the central area boundary
    private boolean isInsideCentralArea(LngLat point, List<LngLat> centralAreaBoundary) {
        return isPointInPolygon(point, centralAreaBoundary);
    }

    // calculate a direct path from start to the destination while avoiding no-fly zones
    private List<LngLat> calculateDirectPath(LngLat start, LngLat end, List<NamedRegion> noFlyZones) {
        List<LngLat> path = new ArrayList<>();
        List<LngLat> centralAreaBoundary = getCentralAreaBoundary();
        boolean hasEnteredCentralArea = false;

        LngLat current = start;

        while (!closeTo(current, end)) {
            path.add(current);


            if (isInsideCentralArea(current, centralAreaBoundary)) {
                hasEnteredCentralArea = true;
            }


            if (hasEnteredCentralArea && !isInsideCentralArea(current, centralAreaBoundary)) {
                throw new IllegalArgumentException("Illegal path: Exited Central Area after entering.");
            }


            current = moveToNextStep(current, end, noFlyZones);
        }

        path.add(end);
        return path;
    }

    // move the drone toward the target while avoiding no-fly zones
    private LngLat moveToNextStep(LngLat current, LngLat target, List<NamedRegion> noFlyZones) {
        LngLat nextStep = moveToward(current, target);
        if (isInNoFlyZone(nextStep, noFlyZones)) {
            nextStep = findAlternativePath(current, target, noFlyZones);
            if (nextStep == null) {
                throw new IllegalArgumentException("No valid path found avoiding no-fly zones.");
            }
        }
        return nextStep;
    }

    // move towards the target by one step
    private LngLat moveToward(LngLat current, LngLat target) {
        double moveDistance = SystemConstants.DRONE_MOVE_DISTANCE;
        double angle = Math.atan2(target.lat() - current.lat(), target.lng() - current.lng());
        double nextLng = current.lng() + moveDistance * Math.cos(angle);
        double nextLat = current.lat() + moveDistance * Math.sin(angle);
        return new LngLat(nextLng, nextLat);
    }

    // check if a point is in no-fly zone
    private boolean isInNoFlyZone(LngLat point, List<NamedRegion> noFlyZones) {
        for (NamedRegion zone : noFlyZones) {
            if (isPointInPolygon(point, Arrays.asList(zone.vertices()))) {
                return true;
            }
        }
        return false;
    }

    private Order withValidationResult(Order order, OrderValidationCode code) {
        Order result = (order == null) ? new Order() : order;
        result.setOrderValidationCode(code);
        return result;
    }

}

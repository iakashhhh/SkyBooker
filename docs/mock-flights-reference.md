# SkyBooker Mock Flights Reference

This document lists the mock flights used by frontend fallback in results.

## When mock flights appear

Mock flights are shown only when:
- selected date has no backend flights,
- next 7-day fallback also has no backend flights,
- and frontend fallback is enabled in results page.

## Mock flight pattern

Mock flights are generated for any searched route.
The route is copied from your search input:
- outbound: ORIGIN -> DESTINATION
- return (round trip): DESTINATION -> ORIGIN

For each mock date, these 2 flights are generated:

| Flight Number | Aircraft     | Departure | Arrival | Duration | Price (INR) | Stops |
|---|---|---|---|---|---:|---:|
| SB-201 | Airbus A320 | 06:20 | 08:35 | 135 mins | 4899 | 0 |
| SB-319 | Boeing 737 | 13:10 | 15:40 | 150 mins | 5620 | 0 |

Seat class is copied from your selected search class.

## Example searches you can run

Use these sample searches from the homepage:

### 1) One-way exact date
- From: Delhi
- To: Mumbai
- Departure: 2026-04-19
- Trip type: One Way

If backend has nothing and fallback reaches mock, you should see SB-201 and SB-319 for DEL -> MUM.

### 2) Round-trip exact date
- From: Delhi
- To: Mumbai
- Departure: 2026-04-19
- Return: 2026-04-22
- Trip type: Round Trip

If backend has no flights, outbound mock should be DEL -> MUM and return mock should be MUM -> DEL.

### 3) Flexible month search
- From: Delhi
- To: Mumbai
- Trip type: One Way
- Date mode: Flexible Dates
- Departure Month: 2026-05

If no real flights in the month, mock fallback will appear with generated date and same two flight numbers.

## Supported city names (mapped to IATA)

- Delhi -> DEL
- Mumbai -> MUM
- Bangalore/Bengaluru -> BLR
- Goa -> GOI
- Hyderabad -> HYD
- Chennai -> MAA
- Kolkata -> CCU
- Jaipur -> JAI
- Pune -> PNQ

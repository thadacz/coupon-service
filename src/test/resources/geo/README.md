`GeoIP2-Country-Test.mmdb` is MaxMind's official test database, copied unchanged from
<https://github.com/maxmind/MaxMind-DB/tree/main/test-data> (Apache License 2.0 or MIT, at your
option; Copyright MaxMind, Inc.). It has the same format and record shape as DB-IP's
IP to Country Lite file used in production, and fixed networks, for example:

| Address | Country |
| --- | --- |
| `55.0.0.4` | DE |
| `2a02:d100::/29` | PL |
| `81.2.69.160/27` | GB |
| `214.1.1.0/24` | none (continent only) |

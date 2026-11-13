import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class Api {
  static const base = String.fromEnvironment('API_BASE', defaultValue: 'http://10.0.2.2:18090/api');
  static const tenant = String.fromEnvironment('TENANT', defaultValue: 'demo');

  static String? token;

  static Future<void> loadToken() async {
    final sp = await SharedPreferences.getInstance();
    token = sp.getString('token');
  }

  static Future<void> saveToken(String value) async {
    token = value;
    final sp = await SharedPreferences.getInstance();
    await sp.setString('token', value);
  }

  static Future<void> clearToken() async {
    token = null;
    final sp = await SharedPreferences.getInstance();
    await sp.remove('token');
  }

  static Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        if (token != null) 'Authorization': 'Bearer $token',
      };

  static dynamic _unwrap(http.Response res) {
    final body = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    if (res.statusCode == 401) throw ApiException('登录已过期，请重新登录', 401);
    if (body['code'] != 0) throw ApiException(body['message']?.toString() ?? '请求失败', body['code'] as int? ?? -1);
    return body['data'];
  }

  static Future<dynamic> get(String path, [Map<String, String>? query]) async {
    final uri = Uri.parse('$base$path').replace(queryParameters: query);
    return _unwrap(await http.get(uri, headers: _headers));
  }

  static Future<dynamic> post(String path, [Map<String, dynamic>? body]) async {
    return _unwrap(await http.post(Uri.parse('$base$path'),
        headers: _headers, body: jsonEncode(body ?? const {})));
  }

  static Future<Map<String, dynamic>> login(String phone) async {
    final data = await post('/open/$tenant/member/login', {'phone': phone, 'source': 'APP'});
    await saveToken(data['token'] as String);
    return Map<String, dynamic>.from(data['member'] ?? data);
  }
}

class ApiException implements Exception {
  final String message;
  final int code;
  ApiException(this.message, this.code);
  @override
  String toString() => message;
}

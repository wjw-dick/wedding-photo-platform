import 'package:flutter/material.dart';
import '../api.dart';
import '../main.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});
  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final phone = TextEditingController();
  bool loading = false;
  String? error;

  Future<void> submit() async {
    if (!RegExp(r'^1\d{10}$').hasMatch(phone.text)) {
      setState(() => error = '请输入正确的手机号');
      return;
    }
    setState(() { loading = true; error = null; });
    try {
      await Api.login(phone.text);
      if (!mounted) return;
      Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const RootShell()));
    } catch (e) {
      setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('光影纪', style: TextStyle(fontSize: 40, letterSpacing: 8)),
              const SizedBox(height: 8),
              const Text('把爱情拍成电影', style: TextStyle(color: Color(0xFF7A6F66), letterSpacing: 2)),
              const SizedBox(height: 48),
              TextField(
                controller: phone,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(labelText: '手机号', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              const Text('生产环境使用微信授权登录；本地联调直接输入手机号。',
                  style: TextStyle(fontSize: 12, color: Color(0xFF7A6F66))),
              if (error != null) Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Text(error!, style: const TextStyle(color: Colors.red)),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                height: 48,
                child: FilledButton(
                  onPressed: loading ? null : submit,
                  child: Text(loading ? '登录中…' : '登录'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

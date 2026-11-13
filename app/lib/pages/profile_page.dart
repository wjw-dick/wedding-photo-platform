import 'package:flutter/material.dart';
import '../api.dart';
import 'login_page.dart';

class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});
  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  Map<String, dynamic> profile = {};
  List coupons = [];
  List addresses = [];

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final p = await Api.get('/member/profile');
      final c = await Api.get('/member/coupons');
      final a = await Api.get('/member/addresses');
      setState(() {
        profile = Map<String, dynamic>.from(p);
        coupons = c ?? [];
        addresses = a ?? [];
      });
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('我的')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: ListTile(
              leading: const CircleAvatar(backgroundColor: Color(0xFFB98B5E), child: Icon(Icons.person, color: Colors.white)),
              title: Text(profile['real_name']?.toString() ?? '会员'),
              subtitle: Text(profile['phone']?.toString() ?? ''),
            ),
          ),
          Card(
            child: Column(children: [
              ListTile(title: const Text('优惠券'), trailing: Text('${coupons.length} 张')),
              const Divider(height: 1),
              ListTile(title: const Text('收货地址'), trailing: Text('${addresses.length} 个')),
              const Divider(height: 1),
              ListTile(title: const Text('积分'), trailing: Text('${profile['points'] ?? 0}')),
              const Divider(height: 1),
              ListTile(title: const Text('储值余额'), trailing: Text('¥${profile['balance'] ?? 0}')),
            ]),
          ),
          const SizedBox(height: 16),
          OutlinedButton(
            onPressed: () async {
              await Api.clearToken();
              if (!context.mounted) return;
              Navigator.of(context).pushAndRemoveUntil(
                  MaterialPageRoute(builder: (_) => const LoginPage()), (_) => false);
            },
            child: const Text('退出登录'),
          ),
        ],
      ),
    );
  }
}

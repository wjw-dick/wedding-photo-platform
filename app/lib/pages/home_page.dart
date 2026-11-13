import 'package:flutter/material.dart';
import '../api.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  Map<String, dynamic> profile = {};
  List orders = [];
  bool loading = true;
  String? error;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() { loading = true; error = null; });
    try {
      final p = await Api.get('/member/profile');
      final o = await Api.get('/member/orders', {'page': '1', 'size': '5'});
      setState(() {
        profile = Map<String, dynamic>.from(p);
        orders = (o is Map ? o['records'] : o) ?? [];
      });
    } catch (e) {
      setState(() => error = e.toString());
    } finally {
      setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('光影纪')),
      body: RefreshIndicator(
        onRefresh: load,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                gradient: const LinearGradient(colors: [Color(0xFF4A3A2E), Color(0xFF9A7B5C)]),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('你好，${profile['real_name'] ?? '会员'}',
                      style: const TextStyle(color: Colors.white, fontSize: 24, letterSpacing: 2)),
                  const SizedBox(height: 8),
                  Text('积分 ${profile['points'] ?? 0} · 储值 ¥${profile['balance'] ?? 0}',
                      style: const TextStyle(color: Colors.white70)),
                ],
              ),
            ),
            const SizedBox(height: 16),
            if (error != null) Text(error!, style: const TextStyle(color: Colors.red)),
            if (loading) const Center(child: Padding(padding: EdgeInsets.all(24), child: CircularProgressIndicator())),
            const Text('最近订单', style: TextStyle(fontSize: 18, letterSpacing: 2)),
            const SizedBox(height: 8),
            ...orders.map((o) => Card(
                  child: ListTile(
                    title: Text(o['order_no']?.toString() ?? '订单'),
                    subtitle: Text('${o['status']} · 拍摄 ${o['shoot_date'] ?? '待定'}'),
                    trailing: Text('¥${o['total_amount'] ?? 0}',
                        style: const TextStyle(color: Color(0xFFB98B5E), fontWeight: FontWeight.w600)),
                  ),
                )),
            if (!loading && orders.isEmpty) const Text('暂无订单', style: TextStyle(color: Color(0xFF7A6F66))),
          ],
        ),
      ),
    );
  }
}

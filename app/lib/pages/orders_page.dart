import 'package:flutter/material.dart';
import '../api.dart';
import 'order_detail_page.dart';

class OrdersPage extends StatefulWidget {
  const OrdersPage({super.key});
  @override
  State<OrdersPage> createState() => _OrdersPageState();
}

class _OrdersPageState extends State<OrdersPage> {
  List orders = [];
  bool loading = true;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final d = await Api.get('/member/orders', {'page': '1', 'size': '30'});
      setState(() => orders = (d is Map ? d['records'] : d) ?? []);
    } finally {
      setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('我的订单')),
      body: RefreshIndicator(
        onRefresh: load,
        child: loading
            ? const Center(child: CircularProgressIndicator())
            : ListView.separated(
                padding: const EdgeInsets.all(16),
                itemCount: orders.length,
                separatorBuilder: (_, __) => const SizedBox(height: 10),
                itemBuilder: (context, i) {
                  final o = orders[i];
                  return Card(
                    child: ListTile(
                      onTap: () => Navigator.of(context).push(MaterialPageRoute(
                          builder: (_) => OrderDetailPage(orderId: o['id'] as int))),
                      title: Text(o['order_no']?.toString() ?? ''),
                      subtitle: Text('${o['package_name'] ?? '婚纱摄影'} · ${o['status']}'),
                      trailing: Text('¥${o['total_amount'] ?? 0}',
                          style: const TextStyle(color: Color(0xFFB98B5E), fontWeight: FontWeight.w600)),
                    ),
                  );
                },
              ),
      ),
    );
  }
}

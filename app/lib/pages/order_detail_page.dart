import 'package:flutter/material.dart';
import '../api.dart';

class OrderDetailPage extends StatefulWidget {
  final int orderId;
  const OrderDetailPage({super.key, required this.orderId});
  @override
  State<OrderDetailPage> createState() => _OrderDetailPageState();
}

class _OrderDetailPageState extends State<OrderDetailPage> {
  Map<String, dynamic> detail = {};
  bool loading = true;
  String? error;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final d = await Api.get('/member/orders/${widget.orderId}');
      setState(() => detail = Map<String, dynamic>.from(d));
    } catch (e) {
      setState(() => error = e.toString());
    } finally {
      setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final order = detail['order'] ?? {};
    return Scaffold(
      appBar: AppBar(title: const Text('订单详情')),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                if (error != null) Text(error!, style: const TextStyle(color: Colors.red)),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(order['order_no']?.toString() ?? '', style: const TextStyle(fontSize: 18)),
                        const SizedBox(height: 8),
                        _line('状态', order['status']),
                        _line('套系', order['package_name']),
                        _line('拍摄日期', order['shoot_date']),
                        _line('总额', '¥${order['total_amount'] ?? 0}'),
                        _line('已付', '¥${order['paid_amount'] ?? 0}'),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                const Text('服务进度', style: TextStyle(fontSize: 18, letterSpacing: 2)),
                const SizedBox(height: 8),
                ...((detail['statusLogs'] as List?) ?? []).map((l) => ListTile(
                      dense: true,
                      leading: const Icon(Icons.check_circle_outline, color: Color(0xFFB98B5E)),
                      title: Text('${l['from_status'] ?? '—'} → ${l['to_status']}'),
                      subtitle: Text(l['created_at']?.toString() ?? ''),
                    )),
                const SizedBox(height: 16),
                FilledButton.tonal(
                  onPressed: () async {
                    final d = await Api.get('/member/orders/${widget.orderId}/downloads');
                    if (!mounted) return;
                    showDialog(
                      context: context,
                      builder: (_) => AlertDialog(
                        title: const Text('交付文件'),
                        content: Text(d is List && d.isNotEmpty ? d.map((x) => x['file_name']).join('\n') : '暂无交付文件'),
                        actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('关闭'))],
                      ),
                    );
                  },
                  child: const Text('查看交付文件'),
                ),
              ],
            ),
    );
  }

  Widget _line(String k, dynamic v) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(children: [
          SizedBox(width: 80, child: Text(k, style: const TextStyle(color: Color(0xFF7A6F66)))),
          Expanded(child: Text(v?.toString() ?? '—')),
        ]),
      );
}

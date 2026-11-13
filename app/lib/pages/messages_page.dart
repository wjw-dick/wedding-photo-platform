import 'package:flutter/material.dart';
import '../api.dart';

class MessagesPage extends StatefulWidget {
  const MessagesPage({super.key});
  @override
  State<MessagesPage> createState() => _MessagesPageState();
}

class _MessagesPageState extends State<MessagesPage> {
  List notices = [];
  bool loading = true;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final d = await Api.get('/member/notices');
      setState(() => notices = d ?? []);
    } finally {
      setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('站内信')),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: load,
              child: ListView.separated(
                padding: const EdgeInsets.all(16),
                itemCount: notices.length,
                separatorBuilder: (_, __) => const SizedBox(height: 8),
                itemBuilder: (_, i) {
                  final n = notices[i];
                  return Card(
                    child: ListTile(
                      leading: Icon(n['read_flag'] == 1 ? Icons.drafts_outlined : Icons.mark_email_unread,
                          color: const Color(0xFFB98B5E)),
                      title: Text(n['title']?.toString() ?? ''),
                      subtitle: Text(n['content']?.toString() ?? ''),
                      trailing: Text(n['created_at']?.toString().substring(0, 10) ?? ''),
                    ),
                  );
                },
              ),
            ),
    );
  }
}

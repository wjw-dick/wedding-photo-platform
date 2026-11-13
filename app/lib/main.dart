import 'package:flutter/material.dart';
import 'api.dart';
import 'pages/login_page.dart';
import 'pages/home_page.dart';
import 'pages/orders_page.dart';
import 'pages/messages_page.dart';
import 'pages/profile_page.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Api.loadToken();
  runApp(const WeddingApp());
}

class WeddingApp extends StatelessWidget {
  const WeddingApp({super.key});

  @override
  Widget build(BuildContext context) {
    const gold = Color(0xFFB98B5E);
    const ink = Color(0xFF2C2622);
    return MaterialApp(
      title: '光影纪',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: gold, primary: gold, surface: const Color(0xFFF7F3EE)),
        scaffoldBackgroundColor: const Color(0xFFF7F3EE),
        appBarTheme: const AppBarTheme(backgroundColor: ink, foregroundColor: Colors.white, centerTitle: true),
        textTheme: const TextTheme(bodyMedium: TextStyle(color: ink)),
      ),
      home: Api.token == null ? const LoginPage() : const RootShell(),
    );
  }
}

class RootShell extends StatefulWidget {
  const RootShell({super.key});
  @override
  State<RootShell> createState() => _RootShellState();
}

class _RootShellState extends State<RootShell> {
  int index = 0;
  final pages = const [HomePage(), OrdersPage(), MessagesPage(), ProfilePage()];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(index: index, children: pages),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (i) => setState(() => index = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: '首页'),
          NavigationDestination(icon: Icon(Icons.receipt_long_outlined), selectedIcon: Icon(Icons.receipt_long), label: '订单'),
          NavigationDestination(icon: Icon(Icons.mail_outline), selectedIcon: Icon(Icons.mail), label: '消息'),
          NavigationDestination(icon: Icon(Icons.person_outline), selectedIcon: Icon(Icons.person), label: '我的'),
        ],
      ),
    );
  }
}
